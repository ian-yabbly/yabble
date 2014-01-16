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
          this.subscribe(Dialog.Event.SHOWN, function() {
            self.find('input[type="text"]:visible').focus();
          });
        };

        AddItemDialog.prototype = $.extend({}, Dialog.prototype);
        
        AddItemDialog.prototype.setupForms = function() {
          this.setupImageUrlForm()
              .setupSearchForm()
              .setupDetailsForm();
        };
        
        AddItemDialog.prototype.setupImageUrlForm = function() {
          var elPreview, previewImage, tmrPreview, txtImageUrl, currentImage,
              self = this;
          
          elPreview = this.find('#image-url-preview');
          txtImageUrl = this.find('#image-url');

          previewImage = function() {
            var tmrShowLoading, error,
                url = $.trim(txtImageUrl.val());

            if(url && (!currentImage || currentImage.attr('src') !== url)) {
              elPreview.empty();
              
              if(tmrShowLoading) { 
                clearTimeout(tmrShowLoading);              
              }
              tmrShowLoading = setTimeout(function() {
                  elPreview.addClass('is-loading');                
              }, 500);

              currentImage = $(
                stringUtils.format(
                  '<img src={} width=512 class=list-item-image>', 
                  url
                )
              );
              
              currentImage.one('load error', function(e) {
                if(tmrShowLoading) { 
                  clearTimeout(tmrShowLoading);              
                }
                elPreview.removeClass('is-loading');
                if(e.type === 'load') {
                  if(error = txtImageUrl.next('.form-error')) {
                    error.remove();
                  }
                  elPreview.append(currentImage);
                } else {
                  formUtils.showError(txtImageUrl, strings.get('item.url.invalid'));
                  currentImage = undefined;
                }
              });
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
            if(isValid) {
              if(!currentImage) {
                formUtils.showError(txtImageUrl, strings.get('item.url.invalid'));
              } else {
                self.showDetailsForm(currentImage.attr('src'));
              }
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
                var i, ii, elItem,
                    results = response && response.d && response.d.results;
                if(!results || results.length === 0) {
                  error = formUtils.showError(
                    txtSearchQuery,
                    strings.get('item.image.search.no-results')
                  )
                } else {
                  for(i = 0, ii = results.length; i < ii; i++) {
                    var image = results[i];
                    elItem = $(
                      tmplImageSearchResultItem({
                        fullUrl     : image.MediaUrl,
                        url         : image.Thumbnail.MediaUrl,
                        width       : 150,
                        height      : (150 / parseFloat(image.Thumbnail.Width)) * 
                          parseFloat(image.Thumbnail.Height)
                      })
                    ).click(function() {
                      var i = $(this).find('img');
                      self.showDetailsForm(i.data('full-url'));
                    })                    
                    elItem.find('img').one('load error', function(e) {
                      if(e.type === 'load') {
                        $(this).parent().removeClass('is-loading');
                      } else {
                        $(this).parent().addClass('has-error');
                      }
                    });
                    self.elResults.append(elItem);
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
            var isValid = formUtils.validateAsNotEmpty(
              txtDescription,
              strings.get('item.body.empty')
            );
            if(isValid) {
              self.showLoading();
            }
            return isValid;
          })
          return this;
        };
        
        AddItemDialog.prototype.setSearchType = function(mode) {
          this.element.removeClass(this.mode);
          this.searchTypes.prop('checked', false);
          this.mode = mode;
          this.searchTypes.filter('[value="' + mode + '"]').prop('checked', true);
          this.element.addClass(mode);
          this.find('input[type="text"]:visible').focus();
          return this;
        };
        
        AddItemDialog.prototype.hideDetailsForm = function() {
          this.element.removeClass('item-details');
          return this;          
        };
        
        AddItemDialog.prototype.showDetailsForm = function(imageUrl) {
          var elImageAndHidden, elImage,
              elImagePreview = this.find('#image-preview');
          elImagePreview.empty().addClass('is-loading');
          elImageAndHidden = $(
            stringUtils.format(
              '<input type=hidden name=image-url value={}>',
              imageUrl
            )
          );
          elImage = $('<img width=100%>');
          elImageAndHidden = elImageAndHidden.add(elImage);
          elImage.one('load error', function(e) {
            if(e.type === 'load') {
              elImagePreview.removeClass('is-loading').append(elImageAndHidden);
            } else {
              elImagePreview.removeClass('is-loading').addClass('has-error');
            }
          }).attr('src', imageUrl);
          this.element.removeClass(this.mode).addClass('item-details');
          return this;
        };
        
        AddItemDialog.prototype.reset = function() {
          this.find('.form-error').remove();
          this.hideLoading();
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