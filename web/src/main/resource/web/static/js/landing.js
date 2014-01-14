/**
 * landing page script
 **/
(function(window, document, require, define, undefined) {
  'use strict';

  require(
      [
        'jquery'
      ],
      function($) {
        $(function() {
          $('#landing-steps').click(function() {
            $('#title').focus();
          })
        })
      }
  );

})(window, document, require, define);
