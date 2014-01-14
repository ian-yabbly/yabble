/**
 * Hash for storing string based content.
 */
(function(window, document, require, define, undefined) {
  'use strict';

  var STRINGS = {
    'create.title.empty': 'What are you trying to decide?',
    'item.desc.empty': 'Describe the item in detail.',
    'item.url.empty': 'Specify an image url.',
    'comment.new.empty': 'Say something awesome.'
  };

  define({
    get: function(key) {
      return STRINGS[key];
    }
  });

})(window, document, require, define);