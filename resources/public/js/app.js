angular.module('libsearch', ['ngRoute', 'ngResource']).

  factory('Libs', function($resource) {
    return $resource('/libs');
  }).

  factory('Repos', function($resource) {
    return $resource('/repos');
  }).

  config(function($routeProvider) {
    $routeProvider.
      when('/libs', { controller: LibCtrl, templateUrl: 'libs.html'}).
      when('/repos', { controller: RepoCtrl, templateUrl: 'repos.html'}).
      when('/about', { templateUrl: 'about.html'}).
      otherwise({redirectTo:'/libs'});
  }).

  directive('navMenu', function($location) {
  return function(scope, element, attrs) {
    var entries = element.find('li'),
        onClass = attrs.navMenu || 'on',
        routePattern,
        entry,
        link,
        url,
        currentLink,
        urlMap = {},
        i;

    if (!$location.$$html5) {
      routePattern = /^#[^/]*/;
    }

    for (i = 0; i < entries.length; i++) {
      entry = angular.element(entries[i]);
      link = entry.find('a');
      url = link.attr('href');

      if ($location.$$html5) {
        urlMap[url] = entry;
      } else {
        urlMap[url.replace(routePattern, '')] = entry;
      }
    }

    scope.$on('$routeChangeStart', function() {
      var pathLink = urlMap[$location.path()];

      if (pathLink) {
        if (currentLink) {
          currentLink.removeClass(onClass);
        }
        currentLink = pathLink;
        currentLink.addClass(onClass);
      }
    });
  };
});

function LibCtrl($scope, $location, Libs) {
  $scope.libs = Libs.query();
  $scope.isActive = function(route) {
    return route === $location.path();
  }
} 

function RepoCtrl($scope, Repos) {
  $scope.repos = Repos.query();
}

function NavCtrl($scope) {

}