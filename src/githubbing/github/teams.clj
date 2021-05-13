(ns githubbing.github.teams
  "GitHub APIs for teams endpoint.
   See https://docs.github.com/en/rest/overview/endpoints-available-for-github-apps#teams"
  (:require [githubbing.github.api :refer :all]
            [githubbing.config :refer [config]]))


(defn get-teams
  "List the teams in the organization"
  ([] (get-teams (:organization config)))
  ([org]
   (get-json (github-url "orgs" org "teams"))))

(defn team-slug
  "Look up a team slug by name, returning null if not found"
  [org name]
  (some #(and (= (:name %) name) (:slug %)) (get-teams org)))

(defn- get-or-set-team-membership
  "Get or or add a team member to a team
  See https://developer.github.com/v3/teams/members/#get-team-membership"
  [json-fn org team-name user-name]
  {:pre [(contains? #{get-json put-json} json-fn)]}
  (if-let [team (if (clojure.string/includes? team-name " ") (team-slug org team-name) team-name)]
    (json-fn (github-url "orgs" org "teams" team "memberships" user-name))))

(defn is-team-member?
  "Return truthy if this user is a member of the team.
  See https://developer.github.com/v3/teams/members/#get-team-membership"
  ([team-name user-name] (is-team-member? (:organization config) team-name user-name))
  ([org team-name user-name]
   (get-or-set-team-membership get-json org team-name user-name)))

(defn add-to-team
  "Add an existing org member to the given team
  See https://developer.github.com/v3/teams/members/#add-or-update-team-membership"
  ([team-member user-name] (add-to-team (:organization config) team-member user-name))
  ([org team-name user-name]
   (get-or-set-team-membership put-json org team-name user-name)))

