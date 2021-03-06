/*
 * Copyright 2015 Open Networking Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 ONOS GUI -- Cluster View Module
 */

(function () {
    'use strict';

    angular.module('ovCluster', [])
        .controller('OvClusterCtrl',
        ['$log', '$scope', 'TableService', 'TableBuilderService',

            function ($log, $scope, ts, tbs) {
                tbs.buildTable({
                    scope: $scope,
                    tag: 'cluster'
                });

                $scope.refresh = function () {
                    $log.debug('Refreshing cluster nodes page');
                    ts.resetSortIcons();
                    $scope.sortCallback();
                };

                $log.log('OvClusterCtrl has been created');
            }]);
}());
