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
      'user-email-dialog',
      'form-utils',
      'mustache',
      'strings',
      'tabset',
      'user',
      'text!template/mustache/contributor.mustache',
      'text!template/mustache/voter.mustache',      
      'menu'
    ],
    function($, utils, AddItemDialog, UserEmailDialog, formUtils, mustache, strings, 
      TabSet, User, textContribTmpl, textVoterTmpl) {
      var dlgAddItem, dlgUserEmail,        
          tmplContributor = mustache.compile(textContribTmpl),
          tmplVoter       = mustache.compile(textVoterTmpl);

      $(function() {
        var changeContribCount,
            elContribCount = $('.contributors-count');

        changeContribCount = function(change) {
          if(elContribCount && elContribCount.size() > 0) {
            elContribCount.text(parseInt(elContribCount.text(), 10) + change);
          }
        };
        utils.exists($('#button-add-item'), function(btnAddItem) {
          var elItemList = $('#list-items ul');
          btnAddItem.click(function() {
            if(!dlgAddItem) {
              dlgAddItem = new AddItemDialog({ 
                 listId : btnAddItem.data('list-id')
               });
            }
            if(elItemList.children().size() > 1 && !User.getLoggedInUser().email) {
              if(!dlgUserEmail) {
                dlgUserEmail = new UserEmailDialog();
              }
              dlgUserEmail.show().done(function() {
                dlgAddItem.show();
              });
            } else {
              dlgAddItem.show();
            }
          });
        });

        utils.exists($('#form-invite-to-list'), function(elFormInviteToList) {
          var txtInviteeEmail = $('#email'),
              listContribs  = $('#list-contributors ul');
              
          if(!User.getLoggedInUser().email) {
            txtInviteeEmail.focus(function() {
              if(!User.getLoggedInUser().email) {
                if(!dlgUserEmail) {
                  dlgUserEmail = new UserEmailDialog();
                }
                txtInviteeEmail.blur();
                dlgUserEmail.show().done(function() {
                  txtInviteeEmail.focus();
                });
              }
              return false;
            })
          }
              
          elFormInviteToList.submit(function() {
            var email = $.trim(txtInviteeEmail.val());
            txtInviteeEmail.focus();
            if(email) {
              elFormInviteToList.removeClass('success error').addClass('loading');
              formUtils.asyncSubmit(elFormInviteToList)
                .done(function() {
                  elFormInviteToList.addClass('success');
                  setTimeout(function() {
                    elFormInviteToList.removeClass('loading success');
                  }, 2000);
                  listContribs.append(tmplContributor({ email : email }));
                  txtInviteeEmail.val('');
                  changeContribCount(1);
                }).fail(function() {                  
                  setTimeout(function() {
                    elFormInviteToList.removeClass('loading error');
                  }, 2000);                  
                  elFormInviteToList.addClass('error');
                })
            } else {
              setTimeout(function() {
                elFormInviteToList.removeClass('loading error');
              }, 2000);
              elFormInviteToList.addClass('error');
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
            var newVoteCount, countText, 
                user        = User.getLoggedInUser(),
                button      = $(this),
                voteCount   = button.data('vote-count'),
                buttonTxt   = button.find('.button-toggle-list-item-vote-text'),
                href        = button.attr('href'),
                isVote      = !button.hasClass('has-voted'),
                elCount     = button.next('.list-item-vote-count'),
                listVoters  = elCount.find('.tooltip ul'),
                elCountText = elCount.find('.list-item-vote-count-text');

            if(user) {
              button.addClass('loading');
              
              $.get(href).done(function() {
                button.removeClass('loading');                
              });
            
              newVoteCount = voteCount + (isVote ? 1 : -1);
              button.data('vote-count', newVoteCount)
                    .toggleClass('has-voted')
                    .attr(
                      'href',
                      isVote ? href + '/delete' : href.replace('/delete', '')
                    );
                  
              buttonTxt.text(
                isVote 
                  ? strings.get('item.vote.delete') 
                  : strings.get('item.vote')
              );
            
              if(newVoteCount > 0) {
                countText = strings.get(
                  'item.voteCount', 
                  newVoteCount, 
                  newVoteCount === 1 ? 'person' : 'people'
                );
              } else {
                countText = strings.get('item.voteCount.zero');
              }
              elCountText.text(countText);

              if(isVote) {
                listVoters.append(
                  tmplVoter({
                    id : user.id,
                    name : user.getDisplayName()
                  })
                )
              } else {
                listVoters.find('li[data-voter-id="' + user.id + '"]').remove();
              }
              if(newVoteCount === 0) {
                elCount.removeClass('tooltip-trigger');
              } else {
                elCount.addClass('tooltip-trigger');
              }
            }
            return false;
          });
        });

        new TabSet({
          tabs : $('.tab')
        });
      })
    }
  );

})(window, document, require, define);