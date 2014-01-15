/**
 * dictionary of strings to be used throughout the application
 */
(function(window, document, require, define, undefined) {
  'use strict';

  var STRINGS = {
    'create.title.empty': 'What are you trying to decide?',
    'item.body.empty': 'Describe the item in detail.',
    'item.url.empty': 'Specify an image url.',
    'item.image.search.empty' : 'Enter a search query.',
    'item.image.search.failed' : 'Doh! Something went wrong.  We&rsquo;re on it.  For now maybe try adding by image url?',
    'item.image.search.no-results' : 'We couldn&rsquo;t find any images for that query.  Try searching for something else.',
    'item.url.invalid' : 'Doh! Something&rsquo;s wrong with that url.  Can you try a different image?',
    'comment.new.empty': 'Say something awesome.'
  };

  define({
    get: function(key) {
      return STRINGS[key];
    }
  });

})(window, document, require, define);