(ns githubbing.github.api
  "API support for github calls.
   See https://docs.github.com/en/rest/overview/endpoints-available-for-github-apps#teams"
  (:require [cheshire.core :as json]
            [clj-http.client :as client]
            [clojure.tools.logging :as log]))

(def api-url-prefix "https://api.github.com")

; GitHub access token, so our REST calls are authenticated to be from you.
(def my-access-token
  (or (System/getenv "GITHUB_TOKEN")
      (println "? Exit, set environment variable GITHUB_TOKEN, and then restart.")))

(def my-github-user
  (or (System/getenv "GITHUB_USER")
      (println "? Exit, set environment variable GITHUB_USER, and then restart.")))

;; Default to max 100 query response size. Github will do <= 100
;; See https://docs.github.com/en/rest/overview/media-types
(def default-http-props {:query-params {:per_page 100}
                         :accept       "application/vnd.github.v3+json"
                         :basic-auth   [my-github-user my-access-token]})

(defn github-url
  "Given a sequence of relative url components, complete the full URL as a string."
  [& more]
  (apply str (interpose "/" (cons api-url-prefix more))))

(defn do-gitHub-get
  "Make a GET REST call to GitHub, expecting json in return.
   Lazy load subsequent pages based on props."
  [url props]
  (log/info (format "GET %s" url))
  (let [response (client/get url (merge default-http-props props))
        content (json/parse-string (:body response) true)]
    (log/info (format "fetched +%s, time: %d" (count content) (:request-time response)))
    (if (and (not (:single-page props))
             (get-in response [:links :next]))
      (concat content (lazy-seq
                        (do-gitHub-get (get-in response [:links :next :href]) props)))
      content)))

;; Execute a possibly paginated GET on a GetHub URL.
;; Memoize it so we don't call it again.
(defonce do-get (memoize do-gitHub-get))

(defn get-json
  "Make a GET call, and catch any exceptions here and call the failure-fn,
   if provided, with the url used and any message from the throwable to get a result.
   Else re-throw the error."
  ([url] (get-json url {}))
  ([url props]
   (let [sanitized-url (first (re-find #"^([^{]+)" url))]
     (try
       (do-get sanitized-url (dissoc props :failure-fn))
       (catch Throwable e
         (if-let [f (:failure-fn props)]
           (f sanitized-url (.getMessage e))
           (throw e)))))))

(defn do-gitHub-put
  "Make a PUT REST call to GitHub with the given map as json payload"
  [url props payload]
  (log/info (format "PUT %s" url))
  (let [body (json/generate-string payload)
        response (client/put url (assoc (merge default-http-props props) :body body))
        content (json/parse-string (:body response) true)]
    (log/info (format "returned %s, time: %d" content (:request-time response)))))

;; No need to memoize a PUT
(def do-put do-gitHub-put)

;; TODO Refactor challenge: rewrite get-json and put-json to eliminate common code
;; without being so clever as to obscure the meaning (!)

(defn put-json
  ([url] (put-json url ""))
  ([url body] (put-json url {} body))
  ([url props body]
   (let [sanitized-url (first (re-find #"^([^{]+)" url))]
     (try
       (do-put sanitized-url (dissoc props :failure-fn) body)
       (catch Throwable e
         (if-let [f (:failure-fn props)]
           (f sanitized-url (.getMessage e))
           (throw e)))))))
