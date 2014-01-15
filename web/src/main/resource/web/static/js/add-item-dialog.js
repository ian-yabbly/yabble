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
        'text!template/mustache/form-add-item.mustache',
        'text!template/mustache/image-search-result.mustache'
      ],
      function($, Dialog, mustache, stringUtils, formUtils, strings, utils, 
        textFormAddItem, textImageSearchResult) {
        var AddItemDialog, dlg, tmplFormAddItem, tmplImageSearchResultItem;

        tmplFormAddItem = mustache.compile(textFormAddItem);
        tmplImageSearchResultItem = mustache.compile(textImageSearchResult)

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
            self.reset();
          });
          
          // Preload the loading image
          $('<img>').attr('src', utils.staticPath('/images/loading.gif'));
        };

        AddItemDialog.prototype = $.extend({}, Dialog.prototype);
        
        AddItemDialog.prototype.setupForms = function() {
          this.setupImageUrlForm()
              .setupSearchForm()
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
        
        AddItemDialog.prototype.clearSearchResults = function() {
          this.elResults.empty();
          return this;
        };
        
        AddItemDialog.prototype.setupSearchForm = function() {
          var self = this, error,
              btnSubmit = this.find('#form-image-search input[type="submit"]'),
              txtSearchQuery = this.find('#image-query');  
              
          this.elResults = this.find('#add-item-image-search-results'),          
              
          this.find('#form-image-search').submit(function() {
            if(error) { 
              error.remove(); 
            }
            if(
              formUtils.validateAsNotEmpty(
                txtSearchQuery, 
                strings.get('item.image.search.empty')
              )
            ) {
              self.clearSearchResults();
              btnSubmit.val('Searching...').attr('disabled', true);
              $.get(
                '/bing/image?query=' + 
                encodeURIComponent($.trim(txtSearchQuery.val()))
              ).done(function(response) {
                var i, ii,
                    results = response && response.d && response.d.results;
                if(!results || results.length === 0) {
                  error = formUtils.showError(
                    txtSearchQuery,
                    strings.get('item.image.search.no-results')
                  )
                } else {
                  for(i = 0, ii = results.length; i < ii; i++) {
                    var image = results[i];
                    self.elResults.append(
                      $(
                        tmplImageSearchResultItem({
                          url   : image.MediaUrl,
                          width : 512,
                          height: (512 / parseFloat(image.Width)) * 
                            parseFloat(image.Height)
                        })
                      ).click(function() {
                        self.showDetailsForm($(this).find('img').attr('src'));
                      })
                    );
                  }
                }
              }).fail(function() {
                error = formUtils.showError(
                  txtSearchQuery,
                  strings.get('item.image.search.failed')
                );
              }).always(function() {
                btnSubmit.val('Search').removeAttr('disabled');
              });
            } 
            return false;
          });
          return this;
        };
        
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
          this.find('.form-error').remove();
          return this.clearSearchResults()
              .hideDetailsForm()
              .setSearchType(AddItemDialog.SearchType.IMAGE_SEARCH);
        };

        AddItemDialog.get = function(listExternalId) {
          // TODO: if this dialog ever needs to be used in the context of 
          // multiple lists, we'll need to write an API for setting
          // the list external id.
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