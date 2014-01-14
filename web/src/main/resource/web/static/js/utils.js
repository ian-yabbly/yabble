/**
 * General Utilities
 */
(function(window, document, require, define, undefined) {
    'use strict';

    var name, transEndEventName, userSessionId,
        enableLogs          = document.body.getAttribute('data-yabbly-enable-logs') === 'true',
        transEndEventNames  = {
            'WebkitTransition' : 'webkitTransitionEnd',
            'MozTransition'    : 'transitionend',
            'OTransition'      : 'oTransitionEnd',
            'msTransition'     : 'MSTransitionEnd',
            'transition'       : 'transitionend'
        };

    for(name in transEndEventNames) {
        if(transEndEventNames.hasOwnProperty(name)) {
            if(typeof document.body.style[name] !== 'undefined') {
                transEndEventName = transEndEventNames[name];
                break;
            }
        }
    }

    define({
        log : function() {
            var args = Array.prototype.slice.apply(arguments);
            if(enableLogs && console) {
                if(args.length > 0 && typeof args[0] === 'string') {
                    args[0] = '[yabble] ' + args[0];
                }
                console.log.apply(console, args);
            }
            return this;
        },
        staticPath : function() {
            return '/s/v-' + document.YABBLE_UI_VERSION;
        },
        requestAnimationFrame : function() {
            var reqFrame = (
                requestAnimationFrame ||
                webkitRequestAnimationFrame ||
                mozRequestAnimationFrame ||
                msRequestAnimationFrame ||
                oRequestAnimationFrame ||
                function( callback ) {
                    window.setTimeout(callback, 1000 / 60);
                }
            );
            return reqFrame.apply(window, arguments);
        },
        onTransitionEnd : function(target, fnc) {
            if(transEndEventName) {
                target.one(transEndEventName, fnc);
            } else {
                fnc();
            }
            return target;
        },
        isArray : function (o) {
            return Object.prototype.toString.call(o) === '[object Array]';
        },
        parseQueryString : function(str) {
            var parts, part, ii,
                i       = str.indexOf('?'),
                query   = i !== -1 ? str.substr(str.indexOf('?') + 1) : '',
                params;
            if(query) {
                params  = {};
                parts   = query.split('&');
                for(i = 0, ii = parts.length; i < ii; i++) {
                    part = parts[i].split('=');
                    params[part.shift()] = part.shift();
                }
            }
            return params;
        },
        getUserSessionId : function() {
            var sessionCookie;
            if(!userSessionId) {
                sessionCookie = document.cookie.match(/yabbly=([A-Z|a-z|0-9]*);?/);
                if(sessionCookie && sessionCookie.length > 1) {
                    sessionCookie = sessionCookie.pop();
                } else {
                    sessionCookie = undefined;
                }
                userSessionId = sessionCookie;
            }
            return userSessionId;
        },
        exists : function(elements, fn) {
            if(elements && elements.size() > 0) {
                fn(elements);
            }
        },
        escapeHtml : function(html) {
            var d = document.createElement('div');
            d.appendChild(document.createTextNode(html));
            return d.innerHTML;
        }
    });

})(window, document, require, define);