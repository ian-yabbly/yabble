/**
 * List Page
 */
(function(window, document, require, define, undefined) {
  'use strict';

  require(
      [
        'jquery',
        'utils',
        'add-item-dialog',
        'form-utils',
        'mustache',
        'strings',
        'tabset',
        'text!template/mustache/contributor.mustache',
        'menu'
      ],
      function($, utils, AddItemDialog, formUtils, mustache, strings, TabSet, textContribTmpl) {
        var tmplContributor = mustache.compile(textContribTmpl);

        $(function() {
          var changeContribCount,
              elContribCount = $('.contributors-count');

          changeContribCount = function(change) {
            if(elContribCount && elContribCount.size() > 0) {
              elContribCount.text(parseInt(elContribCount.text(), 10) + change);
            }
          };

          utils.exists($('#button-add-item'), function(btnAddItem) {
            btnAddItem.click(function() {
              AddItemDialog.get(btnAddItem.data('list-external-id')).show();
            });
          });

          utils.exists($('#form-invite-to-list'), function(elFormInviteToList) {
            var txtInviteeEmail = $('#email'),
                listContribs = $('#list-contributors ul');
            elFormInviteToList.submit(function() {
              var email = $.trim(txtInviteeEmail.val());
              if(email) {
                elFormInviteToList.addClass('loading');
                // TODO:
                // Errors
                formUtils.asyncSubmit(elFormInviteToList)
                    .always(function() {
                      elFormInviteToList.removeClass('loading');
                    })
                    .done(function() {
                      listContribs.append(tmplContributor({ email: email }));
                      txtInviteeEmail.val('');
                      changeContribCount(1);
                    });
              }
              return false;
            });
          });

          utils.exists($('.remove-contributor'), function(elRemoveContrib) {
            elRemoveContrib.one('click', function() {
              var t = $(this);
              $.get(t.attr('href'));
              t.parent().remove();
              changeContribCount(-1);
              return false;
            });
          });

          utils.exists($('.button-toggle-list-item-vote'), function(btnToggleVote) {
            btnToggleVote.click(function() {
              var t = $(this),
                  c = t.children('.list-item-vote-count');
              $.get(t.attr('href'));
              c.text(parseInt(c.text(), 10) + (t.hasClass('has-voted') ? -1 : 1));
              t.toggleClass('has-voted');
              return false;
            });
          });

          new TabSet({
            tabs: $('.tab')
          });
        })
      }
  );

})(window, document, require, define);