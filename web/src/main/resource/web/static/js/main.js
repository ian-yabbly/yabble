/**
 * 'main' entry point for the product site
 *
 * TODO:
 *
 *  - Use r.js optimizer to build to a single file for inclusion in production.  Ultimately it'll look like:
 *
 *          node build-lib/r.js -o baseUrl=yabble-web/src/main/resources/yabble-web/s/js/ \
 *              paths.requireLib=yabble-web/src/main/resources/yabble-web/s/js/vendor/require-2.1.8.js \
 *              name=main \
 *              include=requireLib out=yabble-web/target/main/resources/yabble-web/s/js/main-built.js
 *
 */
(function(window, document, require, define, undefined) {
    'use strict';

    document.YABBLY_UI_VERSION = (function() {
        var m = document.getElementsByName('yabbly-version-hash');
        return m && m[0] && m[0].getAttribute('content');
    }());

    require.config({
        baseUrl : '/s/v-' + document.YABBLY_UI_VERSION + '/js',
        paths   : {
            template    : '../template',
            text        : 'vendor/require-text',
            mustache    : 'vendor/mustache',
            moment      : 'vendor/moment-2.0.0'
        }
    });

    require(
        [
            'jquery',
            'utils',
            'string-utils',
            'create-yabble',
            'list',
            'comments'
        ],
        function($, utils, stringUtils) {
            utils.log(stringUtils.format('yabble.me application version {} started...', document.YABBLY_UI_VERSION));
        }
    );

})(window, document, require, define);