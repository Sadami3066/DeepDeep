(function () {
  'use strict';

  var env = window.__DEEPDEEP_ENV__ || {};
  env.app = env.app || {};
  env.theme = env.theme || {};

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

  function installThemeCss(css) {
    var parent = document.head || document.documentElement;
    if (!parent) {
      pendingThemeCss = css;
      return false;
    }
    var style = document.getElementById('__deepdeep-theme');
    if (!style) {
      style = document.createElement('style');
      style.id = '__deepdeep-theme';
      parent.appendChild(style);
    }
    style.textContent = css;
    return true;
  }

  function applyTheme(theme) {
    env.theme = theme || {};
    var css = buildThemeCss(env.theme);
    if (!installThemeCss(css) && document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', function () {
        installThemeCss(pendingThemeCss || css);
      });
    }
  }

  applyTheme(env.theme);
  window.__deepdeepApplyTheme = applyTheme;

  var deepdeep = {
    env: env,
    info: function () {
      return Promise.resolve({ app: env.app });
    },
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
    toast: toast
  };

  window.deepdeep = deepdeep;
  window.app = deepdeep;
  window.alert = function (message) {
    deepdeep.toast(String(message));
  };
})();
