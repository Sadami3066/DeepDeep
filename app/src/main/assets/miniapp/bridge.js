(function () {
  'use strict';

  var envData = window.__DEEPDEEP_ENV__ || {};
  envData.app = envData.app || {};
  envData.theme = envData.theme || {};
  var listeners = {};

  function bridgeError(message, code) {
    var error = new Error(message);
    error.code = code;
    return error;
  }

  var sequence = 0;
  var pending = {};

  function call(method, params, onEvent) {
    return new Promise(function (resolve, reject) {
      var native = window.DeepDeepNative;
      if (!native || typeof native.postMessage !== 'function') {
        reject(bridgeError('Native bridge is unavailable', 'bridge_unavailable'));
        return;
      }
      var id = ++sequence;
      pending[id] = {
        resolve: resolve,
        reject: reject,
        onEvent: typeof onEvent === 'function' ? onEvent : null
      };
      try {
        native.postMessage(JSON.stringify({ id: id, method: method, params: params || {} }));
      } catch (error) {
        delete pending[id];
        reject(bridgeError('Only JSON values can be sent to the native bridge', 'invalid_params'));
      }
    });
  }

  function parsePayload(payload) {
    if (typeof payload !== 'string') return payload;
    try {
      return JSON.parse(payload);
    } catch (error) {
      return null;
    }
  }

  var pendingPushes = {};

  function emit(type, payload) {
    var entries = listeners[type];
    if (!entries || !entries.length) return;
    entries.slice().forEach(function (entry) {
      try {
        entry(payload);
      } catch (error) {
        console.error(error);
      }
    });
  }

  window.__deepdeepBridge = {
    dispatch: function (payload) {
      var message = parsePayload(payload);
      if (!message || typeof message !== 'object') return;
      if (typeof message.id !== 'number') return;
      var entry = pending[message.id];
      if (!entry) return;
      delete pending[message.id];
      if (message.ok) {
        entry.resolve(message.result);
      } else {
        var detail = message.error || {};
        entry.reject(bridgeError(detail.message || detail.code || 'Bridge request failed', detail.code || 'invalid_request'));
      }
    },
    dispatchEvent: function (payload) {
      var message = parsePayload(payload);
      if (!message || typeof message !== 'object') return;
      if (typeof message.id !== 'number') return;
      var entry = pending[message.id];
      if (!entry || typeof entry.onEvent !== 'function') return;
      if (!message.event || typeof message.event !== 'object') return;
      try {
        entry.onEvent(message.event);
      } catch (error) {
        console.error(error);
      }
    },
    dispatchPush: function (payload) {
      var message = parsePayload(payload);
      if (!message || typeof message !== 'object') return;
      var type = message.type;
      if (typeof type !== 'string' || !type) return;
      var entries = listeners[type];
      if (entries && entries.length) {
        emit(type, message);
        return;
      }
      var queue = pendingPushes[type] || (pendingPushes[type] = []);
      queue.push(message);
      if (queue.length > 16) queue.shift();
    }
  };

  function setData(key, value) {
    var encoded;
    try {
      encoded = JSON.stringify(value);
    } catch (error) {
      return Promise.reject(bridgeError('Value is not JSON serializable', 'invalid_params'));
    }
    if (typeof encoded !== 'string') {
      return Promise.reject(bridgeError('Value is not JSON serializable', 'invalid_params'));
    }
    return call('data.set', { key: key, value: value });
  }

  function toast(message) {
    return call('ui.toast', { text: typeof message === 'string' ? message : String(message) }).then(function () {}, function () {});
  }

  var themeKeys = [
    ['bg', '--dd-bg'],
    ['ink', '--dd-ink'],
    ['inkSecondary', '--dd-ink-secondary'],
    ['accent', '--dd-accent'],
    ['hairline', '--dd-hairline']
  ];
  var pendingThemeCss = null;

  function buildThemeCss(theme) {
    var css = ':root{color-scheme:' + (theme && theme.dark ? 'dark' : 'light') + ';';
    for (var index = 0; index < themeKeys.length; index++) {
      var value = theme ? theme[themeKeys[index][0]] : null;
      if (typeof value === 'string' && value) css += themeKeys[index][1] + ':' + value + ';';
    }
    return css + '}';
  }

  function installStyle(id, css) {
    var parent = document.head || document.documentElement;
    if (!parent) return false;
    var style = document.getElementById(id);
    if (!style) {
      style = document.createElement('style');
      style.id = id;
      parent.appendChild(style);
    }
    style.textContent = css;
    return true;
  }

  function applyTheme(theme) {
    envData.theme = theme || {};
    var css = buildThemeCss(envData.theme);
    if (!installStyle('__deepdeep-theme', css) && document.readyState === 'loading') {
      pendingThemeCss = css;
      document.addEventListener('DOMContentLoaded', function () {
        installStyle('__deepdeep-theme', pendingThemeCss || css);
      });
    }
  }

  var safeAreaKeys = [
    ['top', '--dd-safe-top'],
    ['bottom', '--dd-safe-bottom'],
    ['left', '--dd-safe-left'],
    ['right', '--dd-safe-right']
  ];

  function buildSafeAreaCss(area) {
    var css = ':root{';
    for (var index = 0; index < safeAreaKeys.length; index++) {
      var value = area ? area[safeAreaKeys[index][0]] : null;
      if (typeof value === 'number' && value >= 0) css += safeAreaKeys[index][1] + ':' + value + 'px;';
    }
    return css + '}';
  }

  function applySafeArea(area) {
    envData.safeArea = area || {};
    installStyle('__deepdeep-safe-area', buildSafeAreaCss(envData.safeArea));
  }

  applyTheme(envData.theme);
  applySafeArea(envData.safeArea);
  window.__deepdeepApplyTheme = applyTheme;
  window.__deepdeepApplySafeArea = applySafeArea;

  function on(type, handler) {
    if (typeof type !== 'string' || !type || typeof handler !== 'function') return function () {};
    var entries = listeners[type] || (listeners[type] = []);
    entries.push(handler);
    var queue = pendingPushes[type];
    if (queue && queue.length) {
      delete pendingPushes[type];
      queue.forEach(function (message) {
        try {
          handler(message);
        } catch (error) {
          console.error(error);
        }
      });
    }
    return function () {
      var index = entries.indexOf(handler);
      if (index >= 0) entries.splice(index, 1);
    };
  }

  function off(type, handler) {
    var entries = listeners[type];
    if (!entries) return;
    if (typeof handler !== 'function') {
      delete listeners[type];
      return;
    }
    var index = entries.indexOf(handler);
    if (index >= 0) entries.splice(index, 1);
  }

  var deepdeep = {
    info: function () {
      return Promise.resolve({ app: envData.app });
    },
    env: function () {
      return call('env', {});
    },
    on: on,
    off: off,
    data: {
      get: function (key) {
        return call('data.get', { key: key });
      },
      set: function (key, value) {
        return setData(key, value);
      },
      remove: function (key) {
        return call('data.remove', { key: key });
      },
      keys: function () {
        return call('data.keys', {});
      }
    },
    notify: {
      show: function (options) {
        var source = options || {};
        return call('notify.show', { title: source.title, body: source.body, id: source.id }).then(function (result) {
          return result && result.id;
        });
      },
      cancel: function (id) {
        return call('notify.cancel', { id: id });
      },
      cancelAll: function () {
        return call('notify.cancelAll', {});
      },
      schedule: function (options) {
        var source = options || {};
        return call('notify.schedule', {
          title: source.title,
          body: source.body,
          id: source.id,
          at: source.at,
          repeat: source.repeat
        });
      },
      list: function () {
        return call('notify.list', {});
      }
    },
    files: {
      pick: function (options) {
        var source = options || {};
        return call('files.pick', { types: source.types, multiple: source.multiple });
      },
      read: function (path, encoding) {
        return call('files.read', { path: path, encoding: encoding });
      },
      write: function (path, data, encoding) {
        return call('files.write', { path: path, data: data, encoding: encoding });
      },
      export: function (path) {
        return call('files.export', { path: path });
      },
      url: function (path) {
        return call('files.url', { path: path }).then(function (result) {
          return result && result.url;
        });
      },
      list: function (dir) {
        return call('files.list', { dir: dir });
      },
      delete: function (path) {
        return call('files.delete', { path: path });
      }
    },
    share: {
      text: function (text) {
        return call('share.text', { text: text });
      },
      file: function (path, mimeType) {
        return call('share.file', { path: path, mimeType: mimeType });
      }
    },
    clipboard: {
      read: function () {
        return call('clipboard.read', {}).then(function (result) {
          return result ? result.text : null;
        });
      },
      write: function (text) {
        return call('clipboard.write', { text: text });
      }
    },
    media: {
      pick: function (options) {
        var source = options || {};
        return call('media.pick', { type: source.type, multiple: source.multiple });
      },
      capture: function () {
        return call('media.capture', {});
      },
      saveToGallery: function (path) {
        return call('media.saveToGallery', { path: path });
      },
      scanQr: function () {
        return call('media.scanQr', {}).then(function (result) {
          return result ? result.text : null;
        });
      }
    },
    speech: {
      speak: function (text, lang) {
        return call('speech.speak', { text: text, lang: lang });
      },
      stop: function () {
        return call('speech.stop', {});
      },
      listen: function (options) {
        var source = options || {};
        return call('speech.listen', { timeoutMs: source.timeoutMs });
      }
    },
    location: {
      current: function () {
        return call('location.current', {});
      }
    },
    calendar: {
      list: function (options) {
        var source = options || {};
        return call('calendar.list', { from: source.from, to: source.to, limit: source.limit });
      },
      add: function (options) {
        var source = options || {};
        return call('calendar.add', {
          title: source.title,
          start: source.start,
          end: source.end,
          allDay: source.allDay,
          notes: source.notes,
          location: source.location
        });
      }
    },
    agent: {
      models: function () {
        return call('agent.models', {}).then(function (result) {
          return result && Array.isArray(result.models) ? result.models : [];
        });
      },
      capabilities: function () {
        return call('agent.capabilities', {}).then(function (result) {
          return result || { tools: [], skills: [] };
        });
      },
      run: function (options) {
        var source = options || {};
        var onEvent = typeof source.onEvent === 'function' ? source.onEvent : null;
        var params = {};
        Object.keys(source).forEach(function (key) {
          if (key !== 'onEvent') params[key] = source[key];
        });
        return call('agent.run', params, onEvent).then(function (result) {
          return result && typeof result.text === 'string' ? result.text : '';
        });
      }
    },
    toast: toast,
    haptic: function (intensity) {
      return call('ui.haptic', { intensity: intensity });
    },
    keepScreenOn: function (enabled) {
      return call('ui.keepScreenOn', { enabled: !!enabled });
    },
    openUrl: function (url) {
      return call('system.openUrl', { url: url });
    }
  };

  window.deepdeep = deepdeep;
  window.app = deepdeep;
  window.alert = function (message) {
    deepdeep.toast(String(message));
  };
})();
