/**
 * form utilities
 **/
(function(window, document, require, define, undefined) {
  'use strict';

  var DEFAULT_ERROR_MESSAGE = 'Enter something';

  define(
      [
        'jquery',
        'string-utils',
      ],
      function($, stringUtils) {
        return {
          showError: function(target, message) {
            var n,
                e = $(stringUtils.format('<div class="form-error">{}</div>', message));
            if((n = target.next()).is('.form-error')) {
              n.remove();
            }
            target.after(e);
            return e;
          },
          validateAsNotEmpty: function(inputs, errorMessages) {
            var msg,
                isValid = true,
                self = this;
            inputs.each(function(i, input) {
              input = $(input);
              if($.trim(input.val()).length === 0) {
                isValid = false;
                msg = (
                    (errorMessages && errorMessages[input.prop('id')]) ||
                        (typeof errorMessages === 'string' ? errorMessages : DEFAULT_ERROR_MESSAGE)
                    );
                self.showError(input, msg);
              }
            });
            return isValid;
          },
          asyncSubmit: function(form) {
            return $.ajax({
              method: form.attr('method'),
              url: form.attr('action'),
              data: form.serialize()
            });
          }
        };
      }
  );

})(window, document, require, define);