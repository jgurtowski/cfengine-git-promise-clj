(ns cfengine-git
  (:require
   [clojure.java.io :as io]
   [clojure.java.shell :refer [sh]]
   [clojure.string :as string]
   [clojure.spec.alpha :as spec]
   [cfengine-promise-protocol :as cfepp])
  (:import
   [java.io BufferedReader StringReader]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;Verification Specs for Promiser and Attributes (validate_promise)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(spec/def ::not-nil-string
  (spec/and
   (comp not nil?)
   string?))

(spec/def ::https-path
  (spec/and
   ::not-nil-string
   #(string/starts-with? % "https://")))

(spec/def ::absolute-file-path
  (spec/and
   ::not-nil-string
   #(string/starts-with? % "/")))

(spec/def ::repo (spec/or
                  :https-path ::https-path
                  :local-path ::absolute-file-path))
  
(spec/def ::promise-attributes
  (spec/keys :req-un [::repo]))

(spec/def ::promiser ::absolute-file-path)

;;(spec/explain-str ::promise-attributes {:repo "/home/james"})


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;Evaluation of Promise (evaluate_promise)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;(update-git-repo "/home/james/hub-masterfiles2")
;;Updating
;;Already (when nothing was changed)
(defn update-git-repo [dest-dir]
  (sh "git" "pull" :dir dest-dir))

(defn verify-git-update [{exit :exit
                                out :out
                                err :err}]
  (if (= 0 exit)
    (if (string/starts-with? out "Updating")
      (cfepp/promise-repaired out)
      (cfepp/promise-kept out))
    (cfepp/promise-not-kept err)))

(defn clone-git-repo [repo dest-dir]
  (sh "git" "clone" repo dest-dir))

(defn verify-git-clone [{exit :exit
                          out :out
                          err :err}]
  (if (= 0 exit)
    (cfepp/promise-repaired err) ;; successful clone message comes from 'err' output
    (cfepp/promise-not-kept err)))

(def clone-verify (comp verify-git-clone clone-git-repo))
(def update-verify (comp verify-git-update update-git-repo))

;;(evaluate-promise "/home/james/hub-masterfiles2" {:repo "/git/pulsar-cfengine-masterfiles"})
(defn evaluate-promise [promiser attributes]
  (let [dest-dir promiser
        repo (:repo attributes)]
  (if (.exists (io/file dest-dir))
    (update-verify dest-dir)
    (clone-verify repo dest-dir))))


;; (def example-input-str
;;   (str "cf-agent 3.16.0 v1\n"
;;      "\n"
;;      "{\"operation\": \"validate_promise\", \"log_level\": \"info\", \"promise_type\": \"git\", \"promiser\": \"/home/james/hub-masterfiles2\", \"attributes\": {\"repo\": \"/git/pulsar-cfengine-masterfiles\"}}\n"
;;      "{\"operation\": \"evaluate_promise\", \"log_level\": \"info\", \"promise_type\": \"git\", \"promiser\": \"/home/james/hub-masterfiles2\", \"attributes\": {\"repo\": \"/git/pulsar-cfengine-masterfiles\"}}\n"
;;      "{\"operation\": \"terminate\", \"log_level\": \"info\"}\n"))

;; (def example-input
;;   (BufferedReader. (StringReader. example-input-str)))

(cfepp/start-promise-module (BufferedReader. *in*)
                             "git_promise_module_clj"
                             "0.0.1"
                             ::promiser
                             ::promise-attributes
                             evaluate-promise)
