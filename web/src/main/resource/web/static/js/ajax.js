/**
 * custom ajax handler for interacting with yabbly's server
 **/
(function(window, document, require, define, undefined) {
    'use strict';

    var ResponseStatusCode = {
      SUCCESS         : 'success',
      PARTIAL_SUCCESS : 'partial-success',
      ERROR           : 'error'
    };

    define(
      [
        'jquery',
      ],
      function($) {
        return {
          ajax : function(props) {
            var requestFinished = $.Deferred();
            if(props) {
              if(typeof props !== 'object') {
                props = { url : props };
              }
              if(!props.method) {
                props.method = 'GET';
              }
              $.ajax(props)
                .done(function(response) {
                  if(response && !response.statusCode !== ResponseStatusCode.ERROR) {
                    requestFinished.resolve.apply(requestFinished, arguments);
                  } else {
                    requestFinished.reject.apply(requestFinished, arguments);
                  }
                })
                .fail(function(response) {
                  requestFinished.reject.apply(requestFinished, arguments);
                })
            } else {
              requestFinished.resolve();
            }
            return requestFinished;
          });
        }
      }
    );

})(window, document, require, define);
