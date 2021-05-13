(ns githubbing.github.orgs
  "GitHub APIs for the orgs endpoint.
  See https://docs.github.com/en/rest/overview/endpoints-available-for-github-apps#orgs"
  (:require [githubbing.github.api :refer :all]
            [githubbing.config :refer [config]]))

(defn get-org-members
  "Return members of this organization, defaulting to the config"
  ([] (get-org-members (:organization config)))
  ([org]
   (get-json (github-url "orgs" org "members"))))

(defn add-org-member
  "Add a member to the organization in default member role"
  ([username] (add-org-member (:organization config) username))
  ([org username]
   (put-json (github-url "orgs" org "memberships" username))))
