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
        'xhr',
        'text!template/mustache/dialog-add-user-email.mustache'
      ],
      function($, mustache, User, Dialog, formUtils, strings, xhr, textTmplAddUserEmail) {
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

          this.txtUserEmail = this.find('#txt-user-email');
          
          this.subscribe(Dialog.Event.SHOWN, function() {
            self.txtUserEmail.focus();
          });
          
          this.find('form').submit(function() {
            var txtPassword = self.find('#txt-user-password'),
                password = $.trim(txtPassword.val()),
                isValid = formUtils.validateAsNotEmpty(
                  self.txtUserEmail,
                  strings.get('user.email.empty')
                );
            if(self.element.hasClass('mode-create-account') &&
              (!password || password.length < 6)) {
                formUtils.showError(
                  txtPassword, 
                  strings.get('user.password.invalid')
                );
                isValid = false;
            }
            if(isValid) {              
              self.showLoading();              
              xhr.ajax({
                url : '/me',
                method : 'post',
                data : $(this).serialize()
              }).done(function() {
                User.setLoggedInUserEmail($.trim(self.txtUserEmail.val()));
                self.hide(UserEmailDialog.Status.EMAIL_ENTERED);                
              }).fail(function(xhr, status, error, data) {
                self.hideLoading();
                if(xhr && xhr.status !== 200) {
                  formUtils.showError(
                    (self.element.hasClass('mode-create-account') 
                      ? txtPassword 
                      : self.txtUserEmail
                    ),
                    strings.get('form.miscServerError')
                  );
                } else {
                  if (data && data.form && data.form.email && data.form.email.errors && data.form.email.errors.length) {
                    var errors = data.form.email.errors;
                    var isDuplicate = false;
                    for (var i = 0, ii = errors.length; i < ii; i++) {
                      if (errors[i].code === 'duplicate') {
                        isDuplicate = true;
                        break;
                      }
                    }

                    if (isDuplicate) {
                      formUtils.showError(
                        self.txtUserEmail,
                        strings.get('user.email.duplicate', document.location.pathname || document.location.toString())
                      );
                    } else {
                      formUtils.showError(self.txtUserEmail, strings.get('form.miscServerError'));
                    }
                  }
                }
              })
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
