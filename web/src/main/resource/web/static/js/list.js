/**
 * List Page
 */
(function(window, document, require, define, undefined) {
  'use strict';

  require(
    [
      'jquery',
      'utils',
      'xhr',
      'add-item-dialog',
      'user-email-dialog',
      'form-utils',
      'mustache',
      'strings',
      'string-utils',
      'tabset',
      'user',
      'text!template/mustache/contributor.mustache',
      'text!template/mustache/voter.mustache',      
      'menu'
    ],
    function($, utils, xhr, AddItemDialog, UserEmailDialog, formUtils,
      mustache, strings, stringUtils, TabSet, User, textContribTmpl, 
      textVoterTmpl) {
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
            var user = User.getLoggedInUser();
            if(!dlgAddItem) {
              dlgAddItem = new AddItemDialog({ 
                 listId : btnAddItem.data('list-id')
               });
            }
            if(elItemList.children().size() > 0 && (user && !user.email)) {
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
              user = User.getLoggedInUser(),
              listContribs  = $('#list-contributors ul');
              
          if(user && !user.email) {
            txtInviteeEmail.focus(function() {
              if(!user.email) {
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
        
        utils.exists($('#button-share-list'), function(btnShareList) {
          var txtShareList = $('#list-share-url');
          txtShareList.click(function() {
            txtShareList.select();
          });
          btnShareList.one('click', function() {
            xhr.ajax(
              stringUtils.format('/list/{}/share', btnShareList.data('list-id'))
            ).done(function(response) {
              if(response && response.url) {
                txtShareList
                  .removeClass('is-loading')
                  .val(response && response.url)
                  .select();
              } else {
                txtShareList.removeClass('is-loading').addClass('has-error');                
              }
            }).fail(function() {
              txtShareList.removeClass('is-loading').addClass('has-error');
            });
          });
        });

        new TabSet({
          tabs : $('.tab')
        });
      })
    }
  );

})(window, document, require, define);