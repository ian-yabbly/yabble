/**
 * COMMENT
 */
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
            'text!template/mustache/form-add-item.mustache'
        ],
        function($, Dialog, mustache, stringUtils, formUtils, strings, textFormAddItem) {
            var AddItemDialog, dlg, tmplFormAddItem;

            tmplFormAddItem = mustache.compile(textFormAddItem);

            AddItemDialog = function(props) {
                var elPreview, previewImage, tmrPreview, txtImageUrl, inputs,
                    self = this;

                props = props || {};
                Dialog.call(this, props);

                elPreview   = this.find('#image-preview');
                txtImageUrl = this.find('#image-url');

                previewImage = function() {
                    var elImage,
                        url = $.trim(txtImageUrl.val());

                    if(url) {
                        elPreview.empty()
                            .removeClass('has-error')
                            .addClass('is-loading');

                        elImage = $(stringUtils.format('<img src="{}" width="512" class="list-item-image">', url));
                        elImage.one('load error', function(e) {
                            elPreview.removeClass('is-loading');
                            if(e.type === 'load') {
                                elPreview.append(elImage);
                            } else {
                                elPreview.addClass('has-error');
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

                inputs = $().add(txtImageUrl).add($('#image-desc'));
                this.find('form').submit(function() {
                    var isValid;
                    isValid = formUtils.validateAsNotEmpty(
                        inputs,
                        {
                            'image-url'   : strings.get('item.url.empty'),
                            'image-desc'  : strings.get('item.desc.empty')
                        }
                    );
                    if(isValid) {
                        self.showLoading();
                    }
                    return isValid;
                });
            };

            AddItemDialog.prototype = $.extend({}, Dialog.prototype);

            AddItemDialog.get = function(listExternalId) {
                if(!dlg) {
                    dlg = new AddItemDialog({
                        element : Dialog.createHtml({
                            id      : 'add-item',
                            content : tmplFormAddItem({ listId : listExternalId })
                        }),
                        fixed   : true
                    });
                }
                return dlg;
            };

            return AddItemDialog;
        }
    );

})(window, document, require, define);