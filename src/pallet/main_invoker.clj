(ns pallet.main-invoker
  "Invoke tasks requiring a compute service.  This decouples main from anything
   pallet, jclouds or maven specific, and ensures compiling main doesn't compile
   the world."
  (:require
   [clojure.contrib.logging :as logging]
   [pallet.compute :as compute]
   [pallet.configure :as configure]
   [pallet.blobstore :as blobstore]
   [pallet.utils :as utils]
   [pallet.main :as main]))

(defn log-info
  []
  (logging/debug (format "OS              %s %s"
                         (System/getProperty "os.name")
                         (System/getProperty "os.version")))
  (logging/debug (format "Arch            %s" (System/getProperty "os.arch")))
  (logging/debug (format "Admin user      %s" (:username utils/*admin-user*)))
  (let [private-key-path (:private-key-path utils/*admin-user*)
        public-key-path (:public-key-path utils/*admin-user*)]
    (logging/debug
     (format "private-key-path %s %s" private-key-path
             (.canRead (java.io.File. private-key-path))))
    (logging/debug
     (format "public-key-path %s %s" public-key-path
             (.canRead (java.io.File. public-key-path))))))

(defn find-compute-service
  "Look for a compute service in the following sequence:
     Check pallet.config.service property,
     check maven settings,
     check pallet.config/service var.
   This sequence allows you to specify an overridable default in
   pallet.config/service."
  [defaults project profiles]
  (or
   (compute/compute-service-from-config (:pallet project) profiles)
   (compute/compute-service-from-config defaults profiles)
   (compute/compute-service-from-property)
   (apply compute/compute-service-from-settings profiles)
   (compute/compute-service-from-config-var)))


(defn find-blobstore
  "Look for a compute service in the following sequence:
     Check pallet.config.service property,
     check maven settings,
     check pallet.config/service var.
   This sequence allows you to specify an overridable default in
   pallet.config/service."
  [defaults project profiles]
  (or
   (blobstore/blobstore-from-config (:pallet project) profiles)
   (blobstore/blobstore-from-config defaults profiles)
   (apply blobstore/blobstore-from-settings profiles)))

(defn invoke
  [service user key profiles task params project-options]
  (utils/admin-user-from-config)
  (log-info)
  (let [default-config (configure/pallet-config)
        compute (if service
                  (compute/compute-service
                   service :identity user :credential key)
                  (find-compute-service
                   default-config project-options profiles))
        blobstore (if service
                    (blobstore/service
                     service :identity user :credential key)
                    (find-blobstore
                     default-config project-options profiles))]
    (if compute
      (do
        (logging/debug (format "Running as      %s@%s" user service))
        (try
          (apply task {:compute compute
                       :blobstore blobstore
                       :project project-options} params)
          (finally ;; make sure we don't hang on exceptions
           (compute/close compute)
           (when blobstore
             (blobstore/close blobstore)))))
      (do
        (println "Error: no credentials supplied\n\n")
        (apply (main/resolve-task "help") [])))))
