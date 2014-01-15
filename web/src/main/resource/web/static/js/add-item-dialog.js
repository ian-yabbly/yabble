(function(window, document, require, define, undefined) {
  'use strict';
  
  define(
      [
        'jquery',
        'dialog',
        'mustache',
        'string-utils',
        'form-utils',
        'strings',
        'utils',
        'text!template/mustache/form-add-item.mustache'
      ],
      function($, Dialog, mustache, stringUtils, formUtils, strings, utils, textFormAddItem) {
        var AddItemDialog, dlg, tmplFormAddItem;

        tmplFormAddItem = mustache.compile(textFormAddItem);

        AddItemDialog = function(props) {
          var self = this;

          props = props || {};
          Dialog.call(this, props);
          
          this.setupForms();
                    
          this.searchTypes = this.find('input[name="type"]'); 
          this.searchTypes.change(function() {
            self.setSearchType($(this).val());
          });
          this.setSearchType(
            this.searchTypes.filter('[checked]').val() || 
            AddItemDialog.SearchType.IMAGE_SEARCH
          );
          
          this.subscribe(Dialog.Event.HIDDEN, function() {
            this.setSearchType(AddItemDialog.SearchType.IMAGE_SEARCH);
          });
          
          // Preload the loading image
          $('<img>').attr('src', utils.staticPath('/images/loading.gif'));
        };

        AddItemDialog.prototype = $.extend({}, Dialog.prototype);
        
        AddItemDialog.prototype.setupForms = function() {
          this.setupImageUrlForm()
              .setupDetailsForm();
        };
        
        AddItemDialog.prototype.setupImageUrlForm = function() {
          var elPreview, previewImage, tmrPreview, txtImageUrl, currentImageUrl,
              isImageUrlValid = false,
              self = this;
          
          elPreview = this.find('#image-url-preview');
          txtImageUrl = this.find('#image-url');

          previewImage = function() {
            var elImage, tmrShowLoading, error,
                url = $.trim(txtImageUrl.val());

            if(url && currentImageUrl !== url) {
              currentImageUrl = url;
              elPreview.empty();
              
              if(tmrShowLoading) { 
                clearTimeout(tmrShowLoading);              
              }
              tmrShowLoading = setTimeout(function() {
                  elPreview.addClass('is-loading');                
              }, 500);

              elImage = $(
                stringUtils.format(
                  '<img src="{}" width="512" class="list-item-image">', 
                  url
                )
              );
              
              elImage.one('load error', function(e) {
                if(tmrShowLoading) { 
                  clearTimeout(tmrShowLoading);              
                }
                elPreview.removeClass('is-loading');
                if(e.type === 'load') {
                  if(error = txtImageUrl.next('.form-error')) {
                    error.remove();
                  }
                  isImageUrlValid = true;
                  elPreview.append(elImage);
                } else {
                  isImageUrlValid = false;
                  formUtils.showError(txtImageUrl, strings.get('item.url.invalid'));
                }
              });
            } else {
              if(!url) {
                isImageUrlValid = false;
              }
            }
          };

          txtImageUrl.bind('keydown blur paste', function(e) {
            clearTimeout(tmrPreview);
            if(e.type === 'keydown') {
              tmrPreview = setTimeout(function() {
                previewImage();
              }, 1000);
            } else {
              previewImage();
            }
          });
          
          this.find('#form-image-url').submit(function() {
            var isValid;
            isValid = formUtils.validateAsNotEmpty(
              txtImageUrl,
              strings.get('item.url.empty')
            );
            if(isValid && !isImageUrlValid) {
              formUtils.showError(txtImageUrl, strings.get('item.url.invalid'));
            }
            if(isValid && isImageUrlValid) {
              self.showDetailsForm($.trim(txtImageUrl.val()));
            }
            return false;
          });          
          
          return this;
        }
        
        AddItemDialog.prototype.setupDetailsForm = function() {
          var self = this,
              txtDescription = this.find('#add-item-body');
          this.find('#add-item-back').click(function() {
            self.hideDetailsForm().setSearchType(self.mode);
          })
          this.find('#form-item-details').submit(function() {
            return formUtils.validateAsNotEmpty(
              txtDescription,
              strings.get('item.body.empty')
            );
          })
          return this;
        };
        
        AddItemDialog.prototype.setSearchType = function(mode) {
          this.element.removeClass(this.mode);
          this.searchTypes.prop('checked', false);
          this.mode = mode;
          this.searchTypes.filter('[value="' + mode + '"]').prop('checked', true);
          this.element.addClass(mode);
          return this;
        };
        
        AddItemDialog.prototype.hideDetailsForm = function() {
          this.element.removeClass('item-details');
          return this;          
        };
        
        AddItemDialog.prototype.showDetailsForm = function(imageUrl) {
          var elImagePreview = this.find('#image-preview');
          this.element.removeClass(this.mode);
          elImagePreview.append(
            stringUtils.format(
              '<img src="{}" width="512" class="list-item-image">', 
              imageUrl
            )
          );
          this.element.addClass('item-details');
          return this;
        };
        
        AddItemDialog.prototype.reset = function() {
          this.hideDetailsForm()
              .setSearchType(AddItemDialog.SearchType.IMAGE_SEARCH);
          return this;
        };

        AddItemDialog.get = function(listExternalId) {
          if(!dlg) {
            dlg = new AddItemDialog({
              element : Dialog.createHtml({
                id      : 'add-item',
                content : tmplFormAddItem({ listId: listExternalId })
              }),
              fixed   : true
            });
          }
          return dlg;
        };
        
        AddItemDialog.SearchType = {
          IMAGE_SEARCH  : 'image-search',
          IMAGE_URL     : 'image-url'
        };
        
        return AddItemDialog;
      }
  );

})(window, document, require, define);