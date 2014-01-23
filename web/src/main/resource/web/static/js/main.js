/**
 * main entry point for the product site
 */
(function(window, document, require, define, undefined) {
  'use strict';

  document.YABBLE_UI_VERSION = (function() {
    var m = document.getElementsByName('yabble-version-hash');
    return m && m[0] && m[0].getAttribute('content');
  }());

  require.config({
    baseUrl: '/s/' + document.YABBLE_UI_VERSION + '/js',
    paths: {
      template  : '../template',
      text      : 'vendor/require-text',
      mustache  : 'vendor/mustache',
      moment    : 'vendor/moment-2.0.0'
    }
  });

  require(
      [
        'jquery',
        'utils',
        'string-utils',
        'create-yabble',
        'landing',
        'list',
        'comments'
      ],
      function($, utils, stringUtils) {
        utils.log(stringUtils.format('yabble.me application version {} started...', document.YABBLE_UI_VERSION));
      }
  );

})(window, document, require, define);