(function(window, document, require, define, undefined) {
    'use strict';

    require(
        [
            'jquery',
            'utils',
            'strings',
            'form-utils',
            'mustache',
            'moment',
            'text!template/mustache/comment.mustache'
        ],
        function($, utils, strings, formUtils, mustache, moment, textCommentTmpl) {
            var reNewLine = /\n/g;

            $(function() {
                var elCommentCount  = $('.comment-count'),
                    tmplComment     = mustache.compile(textCommentTmpl);

                utils.exists($('.add-comment-form'), function(frmAddNewComment) {
                    frmAddNewComment.submit(function() {
                        var e,
                            listComments,
                            t           = $(this),
                            txtBody     = t.find('textarea'),
                            hdnName     = t.find('input[name="username"]'),
                            hdnImageUrl = t.find('input[name="user-image-url"]');

                        if(
                            formUtils.validateAsNotEmpty(
                                txtBody,
                                strings.get('comment.new.empty')
                            )
                        ) {
                            listComments    = t.parents('.comments-list').find('ul');
                            listComments.append(
                                tmplComment({
                                    body            : $.trim(utils.escapeHtml(txtBody.val()).replace(reNewLine, '<br>')),
                                    userName        : $.trim(hdnName.val()),
                                    userImageUrl    : $.trim(hdnImageUrl.val()),
                                    created         : moment().fromNow()
                                })
                            );
                            elCommentCount.text(parseInt(elCommentCount.text(), 10) + 1);
                            formUtils.asyncSubmit(t);
                            if(e = txtBody.next('.form-error').size() > 0) {
                                e.remove();
                            }
                            txtBody.val('');
                        }
                        return false;
                    });
                });

                utils.exists($('.comment-delete'), function(lnkRemoveComment) {
                    lnkRemoveComment.one('click', function() {
                        var t = $(this);
                        $.get(t.attr('href'));
                        t.parent().remove();
                        elCommentCount.text(parseInt(elCommentCount.text(), 10) - 1);
                        return false;
                    });
                });
            });
        }
    );

})(window, document, require, define);