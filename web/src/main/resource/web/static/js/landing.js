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
          var txtTitle = $('#title');
          $('#landing-steps').click(function() {
            txtTitle.focus();
          })
          if(document.location && document.location.hash == '#create-yabble') {
            txtTitle.focus();
          }
        })
      }
  );

})(window, document, require, define);
