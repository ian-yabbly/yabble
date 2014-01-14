/**
 * javascript used by the yabble creation flow
 **/
(function(window, document, require, define, undefined) {
    'use strict';

    require(
        [
            'jquery',
            'utils',
            'form-utils',
            'strings',
            'string-utils'
        ],
        function($, utils, formUtils, strings, stringUtils) {
            var initNewYabbleForm, initInviteForm;

            initNewYabbleForm = function(elForm) {
                elForm.submit(function() {
                    return formUtils.validateAsNotEmpty(
                        //$('#title, #body'),
                        $('#title'),
                        {
                            title : strings.get('create.title.empty'),
                            body  : strings.get('create.body.empty')
                        }
                    );
                });
            };

            initInviteForm = function(elForm) {
                var addNewInvitee,
                    txtInviteeEmail = $('#email'),
                    elEnterInvite   = $('#invite-entry'),
                    listInvitees    = $('#invited');

                addNewInvitee = function() {
                    var email = $.trim(txtInviteeEmail.val()),
                        elInvitee;
                    if(email.length > 0 && email.indexOf('@') > 0) {
                        elInvitee = $(
                            stringUtils.format(
                                '<li><input type=hidden name=email value="{}">{}<button class="gray-button">-</button></li>',
                                email,
                                email
                            )
                        );
                        elInvitee.find('button').one('click', function() {
                           elInvitee.remove();
                        });
                        listInvitees.append(elInvitee);
                        txtInviteeEmail.val('');
                    }
                };

                elEnterInvite.find('button').click(function() {
                    addNewInvitee();
                    return false;
                });

                txtInviteeEmail.keydown(function(e) {
                    if(e.keyCode === 13) {
                        addNewInvitee();
                        return false;
                    }
                });

                elForm.submit(function() {
                    if(listInvitees.children().size() === 0) {
                        formUtils.showError(
                            elForm.find('input[type="submit"]'),
                            strings.get('create.invite.empty')
                        );
                        return false;
                    }
                });
            };

            $(function() {
                utils.exists($('#form-new-yabble'), initNewYabbleForm);
                utils.exists($('#form-invite'), initInviteForm);
            });
        }
    );

})(window, document, require, define);
