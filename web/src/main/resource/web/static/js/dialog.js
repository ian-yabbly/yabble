/**
 * COMMENT
 */
(function(window, document, require, define, undefined) {
  'use strict';

  define(
      [
        'jquery',
        'observable',
        'utils',
        'string-utils',
        'mustache',
        'text!template/mustache/dialog.mustache'
      ],
      function($, Observable, utils, stringUtils, mustache, txtDialogTmpl) {

        var overlay, container, visibleDialog, body, containers,
            init, Dialog, tmpl;

        tmpl = mustache.compile(txtDialogTmpl);

        init = function() {
          body = $(document.body);

          // We use a single modal overlay for all dialogs on a given page.
          overlay = $('#dialog-overlay');
          if(overlay.length === 0) {
            overlay = $('<div id="dialog-overlay"></div>').appendTo(body);
          }

          // We use a single dialog container for all dialogs on a given page
          container = $('#dialog-body');
          if(container.length === 0) {
            container = $('<div id="dialog-container"></div>').appendTo(body);
          }

          container.click(function(e) {
            var target = $(e.target);
            if(visibleDialog && target !== visibleDialog.element
                && target.parents('.active-dialog').length === 0) {
              visibleDialog.hide();
            }
          });

          containers = $().add(container).add(overlay);

          init = undefined;
        };

        Dialog = function(props) {
          init && init();
          props = props || {};
          if(props instanceof $) {
            props = { element: props };
          }
          if(!props.element || !(props.element instanceof $) || props.element.length === 0) {
            throw 'You must specify an element to display as the dialog.';
          }

          Observable.call(this);

          this.fixed = props.fixed;
          if(!props.doNotDetach) {
            this.element = props.element.detach().appendTo(container);
          } else {
            this.element = props.element;
          }

          this.getDimensions();
          this.setCloseButtonListener();

          if(body.hasClass('dialog-visible') && this.element.hasClass('active-dialog')) {
            containers.css('display', 'block');
            visibleDialog = this;
          }
        };

        Dialog.prototype = $.extend({}, Observable.prototype);

        Dialog.prototype.getDimensions = function() {
          this.height = this.element.outerHeight();
          this.width = this.element.outerWidth();
          return this;
        };

        Dialog.prototype.setCloseButtonListener = function() {
          var self = this;
          this.element.find('.dialog-close').click(function() {
            self.hide();
          }.bind(this));
          return this;
        };

        Dialog.prototype.bindResizeListeners = function() {
          var self = this;
          this.updatePositionListener = function() {
            self.setPosition();
          };
          $(window).bind('resize', this.updatePositionListener);
          return this;
        };

        Dialog.prototype.unbindResizeListeners = function() {
          if(this.updatePositionListener) {
            $(window).unbind('resize', this.updatePositionListener);
          }
          return this;
        };

        Dialog.prototype.setPosition = function(setY, setX) {
          var top, left,
              props = {};
          setY = typeof setY === 'undefined' ? true : setY;
          setX = typeof setX === 'undefined' ? true : setX;
          if(setY) {
            top = ((document.body.clientHeight - this.height) / 2) + 'px';
            props.top = top;
          }
          if(setX) {
            left = ((document.body.clientWidth - this.width) / 2) + 'px';
            props.left = left;
          }
          this.element.css(props);
          return this;
        };

        Dialog.prototype.show = function() {
          var self = this,
              show = function() {
                visibleDialog = self;
                containers.css('display', 'block');
                utils.requestAnimationFrame(function() {
                  self.getDimensions();
                  if(!self.fixed) {
                    self.setPosition();
                    self.bindResizeListeners();
                  }
                  utils.onTransitionEnd(
                      self.element,
                      function() {
                        body.addClass('no-scroll');
                        self.publish(Dialog.Event.SHOWN, self);
                      }
                  );
                  self.element.addClass('active-dialog');
                  body.addClass('dialog-visible');
                });
              };
          if(!this.isVisible()) {
            if(visibleDialog) {
              visibleDialog.once(
                  Dialog.Event.HIDDEN,
                  show
              ).hide();
            } else {
              show();
            }
          }
          return this;
        };

        Dialog.prototype.hide = function(arg1, arg2, ar3) {
          var self = this,
              args = Array.prototype.slice.apply(arguments);
          if(visibleDialog === this) {
            utils.onTransitionEnd(
                container,
                function() {
                  self.element.removeClass('active-dialog');
                  containers.css('display', '');
                  body.removeClass('no-scroll');
                  self.hideLoading();
                  self.publish.apply(self, [ Dialog.Event.HIDDEN, self ].concat(args));
                }
            );
            body.removeClass('dialog-visible');
            if(!this.fixed) {
              this.unbindResizeListeners();
            }
            visibleDialog = undefined;
          }
          return this;
        };

        Dialog.prototype.showLoading = function() {
          this.element.addClass('dialog-loading');
        };

        Dialog.prototype.hideLoading = function() {
          this.element.removeClass('dialog-loading');
        };

        Dialog.prototype.isVisible = function() {
          return visibleDialog === this;
        };

        Dialog.prototype.find = function(selector) {
          return this.element.find(selector);
        };

        Dialog.visibleDialog = function() {
          return visibleDialog;
        };

        Dialog.createHtml = function(props) {
          var el;
          props = props || {};
          init && init();
          container.append(el = $(tmpl(props)));
          return el;
        };

        Dialog.Event = {
          SHOWN: 'shown',
          HIDDEN: 'hidden'
        };

        return Dialog;
      }
  );

})(window, document, require, define);