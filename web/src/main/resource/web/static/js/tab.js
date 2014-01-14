(function(window, document, require, define, undefined) {
    'use strict';

    define(
        [
            'jquery',
            'observable'
        ],
        function($, Observable) {

            var Tab,
                CssClass    = {
                    ACTIVE_TAB          : 'active-tab',
                    ACTIVE_TAB_CONTENT  : 'active-tab-content'
                };

            Tab = function(id, btn, content) {
                this.id                 = id;
                this.btn                = btn;
                this.content            = content;
                Observable.call(this);
            };

            Tab.prototype = $.extend({}, Observable.prototype);

            Tab.prototype.activate = function() {
                this.btn.addClass(CssClass.ACTIVE_TAB);
                this.content.addClass(CssClass.ACTIVE_TAB_CONTENT);
                this.publish(Tab.Event.ACTIVATED, this);
                return this;
            };

            Tab.prototype.deActivate = function() {
                this.btn.removeClass(CssClass.ACTIVE_TAB);
                this.content.removeClass(CssClass.ACTIVE_TAB_CONTENT);
                this.publish(Tab.Event.ACTIVATED, this);
                return this;
            };

            Tab.prototype.isActive = function() {
                return this.btn.hasClass(CssClass.ACTIVE_TAB) && this.content.hasClass(CssClass.ACTIVE_TAB_CONTENT);
            };

            Tab.Event = {
                ACTIVATED   : 'activated',
                DEACTIVATED : 'deactivated'
            };

            return Tab;
        }
    );

})(window, document, require, define);