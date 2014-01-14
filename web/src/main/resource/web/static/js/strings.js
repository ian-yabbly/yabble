/**
 * Hash for storing string based content.
 */
(function(window, document, require, define, undefined) {
    'use strict';

    var STRINGS = {
        'create.title.empty'    : 'Give your Yabble a descriptive title.',
        'create.body.empty'     : 'Describe your Yabble so that collaborators have an idea of what you&rsquo;re trying to decide.',
        'create.invite.empty'   : 'Invite at least one collaborator by entering their email address.',
        'item.desc.empty'       : 'Describe the item in detail.',
        'item.url.empty'        : 'Specify an image url.',
        'comment.new.empty'     : 'Say something awesome.'
    };

    define({
        get : function(key) {
            return STRINGS[key];
        }
    });

})(window, document, require, define);