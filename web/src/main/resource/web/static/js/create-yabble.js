/**
 * javascript used by the yabble creation flow
 **/
(function(window, document, require, define, undefined) {
  'use strict';

  require(
      [
        'jquery',
        'utils',
        'form-utils',
        'strings',
        'string-utils'
      ],
      function($, utils, formUtils, strings, stringUtils) {
        $(function() {
          utils.exists($('#form-new-yabble'), function(elForm) {
            elForm.submit(function() {
              return formUtils.validateAsNotEmpty(
                  $('#title'),
                  {
                    title: strings.get('create.title.empty')
                  }
              );
            });
          });
        });
      }
  );

})(window, document, require, define);
