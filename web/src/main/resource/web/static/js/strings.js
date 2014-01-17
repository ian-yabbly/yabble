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
    'item.vote' : 'Vote for this',
    'item.vote.delete' : 'Remove your vote',
    'item.voteCount.zero' : 'no one has voted for this option',
    'item.voteCount' : '{} {} voted for this option',
    'comment.new.empty' : 'Say something awesome.',
    'user.me' : 'You',
    'user.email.empty' : 'Enter your email.',
    'user.password.invalid' : 'Enter a password that is at least 6 characters long.',
    'form.miscServerError' : 'Doh! Something went wrong. We&rsquo;re on it.'
  };

  define(
    [
      'string-utils',
    ],
    function(stringUtils) {
      return {
        get: function(key) {
          var str,
              args = Array.prototype.slice.apply(arguments);
          args && args.shift();
          str = STRINGS[key];
          if(str && args.length > 0) {
            str = stringUtils.format.apply(stringUtils, [ str ].concat(args));
          }
          return str;
        }
      };
    }
  );

})(window, document, require, define);