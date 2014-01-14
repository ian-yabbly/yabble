/**
 * Responsible for the inclusion of the appropriate jQuery version and disabling
 * of jQuery's global variables.
 */
(function(window, document, require, define, undefined) {
  'use strict';

  define(
      [
        'jquery',
        'utils'
      ],
      function($, utils) {
        var Observable;

        Observable = function() {
          this.listeners = {};
        };

        Observable.prototype.subscribe = function(event, listener) {
          var i, ii;
          if(utils.isArray(event)) {
            for(i = 0, ii = event.length; i < ii; i++) {
              this.subscribe(event[i], listener);
            }
          } else {
            if(!this.listeners[event]) {
              this.listeners[event] = [];
            }
            if(typeof listener === 'function') {
              this.listeners[event].push(listener);
            }
          }
          return this;
        };

        Observable.prototype.unsubscribe = function(event, listener) {
          var i;
          if(this.listeners[event] && (i = this.listeners[event].indexOf(listener)) !== -1) {
            this.listeners[event].splice(i, 1);
          }
          return this;
        };

        Observable.prototype.once = function(event, listener) {
          var self = this,
              f = function() {
                self.unsubscribe(event, f);
                if(typeof listener === 'function') {
                  listener.apply(self, arguments);
                }
              };
          return this.subscribe(event, f);
        };

        Observable.prototype.publish = function(event) {
          var i, ii,
              listeners = this.listeners[event] && this.listeners[event].slice();
          if(listeners) {
            for(i = 0, ii = listeners.length; i < ii; i++) {
              listeners[i].apply(this, arguments);
            }
          }
          return this;
        };

        return Observable;
      }
  );

})(window, document, require, define);