(function(window, document, require, define, undefined) {
    'use strict';

    define(
      [
        'jquery',
        'mustache',
        'user',        
        'dialog',
        'form-utils',
        'strings',
        'text!template/mustache/dialog-add-user-email.mustache'
      ],
      function($, mustache, User, Dialog, formUtils, strings, textTmplAddUserEmail) {
        var tmplAddUserEmail = mustache.compile(textTmplAddUserEmail);
        
        var UserEmailDialog = function(props) {
          var self = this;
          
          props = $.extend(props || {}, {
            element : Dialog.createHtml({
              id : 'dialog-user-email',
              content : tmplAddUserEmail({ 
                redirUrl : encodeURIComponent(document.location) 
              })
            })
          });
          Dialog.call(this, props);
          
          this.find('#create-account').click(function() {
            self.element.addClass('mode-create-account');
          });

          this.txtUserEmail = this.find('#user-email');
          
          this.subscribe(Dialog.Event.SHOWN, function() {
            self.txtUserEmail.focus();
          });
          
          this.find('form').submit(function() {
            var isValid = formUtils.validateAsNotEmpty(
              self.txtUserEmail,
              strings.get('user.email.empty')
            );
            if(isValid) {
              // TODO: Actually post to server
              self.showLoading();
              setTimeout(function() {
                User.setLoggedInUserEmail($.trim(self.txtUserEmail.val()));
                self.hide(UserEmailDialog.Status.EMAIL_ENTERED);
              }, 1000);
            }
            return false;
          });
          
          this.subscribe(Dialog.Event.HIDDEN, function() {
            self.reset()
          });
        };
        
        UserEmailDialog.prototype = $.extend({}, Dialog.prototype);
        
        UserEmailDialog.prototype.show = function() {
          var hidden = $.Deferred();
          Dialog.prototype.show.call(this);
          this.once(Dialog.Event.HIDDEN, function(event, dlg, status) {
            if(status === UserEmailDialog.Status.EMAIL_ENTERED) {
              hidden.resolve();
            } else {
              hidden.reject();
            }
          });
          return hidden;
        };
        
        UserEmailDialog.prototype.reset = function() {
          this.element.removeClass('mode-create-account');
          this.find('.form-error').remove();
          this.find('input[type="email"], input[type="password"]').val('');
          return this;
        };
        
        UserEmailDialog.Status = {
          EMAIL_ENTERED : 'email-entered',
          ERROR : 'error'          
        };
        
        return UserEmailDialog;
      }
    );

})(window, document, require, define);
