(function () {
  'use strict';

  var env = window.__DEEPDEEP_ENV__ || {};
  env.app = env.app || {};
  env.permissions = env.permissions || [];
  env.capabilities = env.capabilities || [];
  env.theme = env.theme || {};

  function bridgeError(message, code) {
    var error = new Error(message);
    error.code = code;
    return error;
  }

  var sequence = 0;
  var pending = {};

  function call(method, params) {
    return new Promise(function (resolve, reject) {
      var native = window.DeepDeepNative;
      if (!native || typeof native.postMessage !== 'function') {
        reject(bridgeError('Native bridge is unavailable', 'bridge_unavailable'));
        return;
      }
      var id = ++sequence;
      pending[id] = { resolve: resolve, reject: reject };
      try {
        native.postMessage(JSON.stringify({ id: id, method: method, params: params || {} }));
      } catch (error) {
        delete pending[id];
        reject(bridgeError('Only JSON values can be sent to the native bridge', 'invalid_params'));
      }
    });
  }

  var activeRun = null;
  var progressListeners = [];

  function dispatchAgentEvent(message) {
    var event = { type: message.type };
    if (message.type === 'text') event.text = typeof message.delta === 'string' ? message.delta : '';
    if (message.type === 'tool') event.name = typeof message.name === 'string' ? message.name : '';
    if (activeRun && typeof activeRun.onEvent === 'function') {
      try {
        activeRun.onEvent(event);
      } catch (error) {}
    }
    var listeners = progressListeners.slice();
    for (var index = 0; index < listeners.length; index++) {
      try {
        listeners[index](event);
      } catch (error) {}
    }
  }

  window.__deepdeepBridge = {
    dispatch: function (payload) {
      var message = payload;
      if (typeof payload === 'string') {
        try {
          message = JSON.parse(payload);
        } catch (error) {
          return;
        }
      }
      if (!message || typeof message !== 'object') return;
      if (typeof message.id === 'number') {
        var entry = pending[message.id];
        if (!entry) return;
        delete pending[message.id];
        if (message.ok) {
          entry.resolve(message.result);
        } else {
          var detail = message.error || {};
          entry.reject(bridgeError(detail.message || detail.code || 'Bridge request failed', detail.code || 'invalid_request'));
        }
        return;
      }
      if (message.event === 'agent') {
        dispatchAgentEvent(message);
      }
    }
  };

  function run(optionsOrPrompt, maybeContext) {
    return new Promise(function (resolve, reject) {
      if (activeRun) {
        reject(bridgeError('An agent run is already active', 'busy'));
        return;
      }
      var options = {};
      if (typeof optionsOrPrompt === 'string') {
        options.prompt = optionsOrPrompt;
        if (maybeContext !== undefined) options.context = maybeContext;
      } else if (optionsOrPrompt && typeof optionsOrPrompt === 'object') {
        options = optionsOrPrompt;
      }
      var prompt = typeof options.prompt === 'string' ? options.prompt : '';
      if (!prompt.trim()) {
        reject(bridgeError('prompt must be a non-empty string', 'invalid_params'));
        return;
      }
      var params = { prompt: prompt };
      if (options.context !== undefined) params.context = options.context;
      activeRun = { onEvent: typeof options.onEvent === 'function' ? options.onEvent : null };
      call('agent.run', params).then(function (result) {
        activeRun = null;
        if (typeof result === 'string') {
          resolve(result);
        } else if (result && typeof result.text === 'string') {
          resolve(result.text);
        } else {
          resolve('');
        }
      }, function (error) {
        activeRun = null;
        reject(error);
      });
    });
  }

  function cancel() {
    return call('agent.cancel', {}).then(function (result) {
      return result === true;
    });
  }

  function onProgress(listener) {
    if (typeof listener !== 'function') return function () {};
    progressListeners.push(listener);
    var removed = false;
    return function () {
      if (removed) return;
      removed = true;
      var index = progressListeners.indexOf(listener);
      if (index >= 0) progressListeners.splice(index, 1);
    };
  }

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

  function reportError(message, source, line, column, stack) {
    var native = window.DeepDeepNative;
    if (!native || typeof native.postMessage !== 'function') return;
    try {
      native.postMessage(JSON.stringify({
        event: 'log.error',
        message: String(message),
        source: source || null,
        line: typeof line === 'number' ? line : null,
        column: typeof column === 'number' ? column : null,
        stack: stack || null
      }));
    } catch (error) {}
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

  window.addEventListener('error', function (event) {
    var target = event.target;
    var message = event.message;
    if (!message && target && (target.src || target.href)) {
      message = 'Failed to load resource: ' + (target.src || target.href);
    }
    reportError(
      message || 'Uncaught error',
      event.filename || (target && (target.src || target.href)) || null,
      typeof event.lineno === 'number' ? event.lineno : null,
      typeof event.colno === 'number' ? event.colno : null,
      event.error && event.error.stack ? String(event.error.stack) : null
    );
  }, true);

  window.addEventListener('unhandledrejection', function (event) {
    var reason = event.reason;
    var message = reason && typeof reason.message === 'string' ? reason.message : String(reason);
    reportError(message, null, null, null, reason && reason.stack ? String(reason.stack) : null);
  });

  var deepdeep = {
    env: env,
    info: function () {
      return Promise.resolve({
        app: env.app,
        capabilities: env.capabilities
      });
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
      run: run,
      cancel: cancel,
      onProgress: onProgress
    },
    toast: toast,
    log: {
      error: function (message, stack) {
        var text;
        var trace = stack;
        if (message && typeof message.message === 'string') {
          text = message.message;
          if (!trace && typeof message.stack === 'string') trace = message.stack;
        } else {
          text = String(message);
        }
        reportError(text, null, null, null, trace ? String(trace) : null);
      }
    }
  };

  window.deepdeep = deepdeep;
  window.app = deepdeep;
  window.alert = function (message) {
    deepdeep.toast(String(message));
  };
})();
