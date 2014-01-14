/**
 * Responsible for the inclusion of the appropriate jQuery version and disabling
 * of jQuery's global variables.
 */
(function(window, document, require, define, undefined) {
    'use strict';

    define([ 'vendor/jquery-1.10.2' ], function() {
        return jQuery.noConflict(true);
    });

})(window, document, require, define);