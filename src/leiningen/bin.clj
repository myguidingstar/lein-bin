(ns leiningen.bin
  "Create a standalone executable for your project."
  (:require [clojure.string :refer [join]]
            [leiningen.jar :refer [get-jar-filename]]
            [leiningen.uberjar :refer [uberjar]]
            [me.raynes.fs :as fs]
            [clojure.java.io :as io])
  (:import java.io.FileOutputStream))

(defn- jvm-options [{:keys [jvm-opts name version] :or {jvm-opts []}}]
  (join " " (conj jvm-opts (format "-client -D%s.version=%s" name version))))

(defn jar-preamble [flags]
  (format (str ":;exec java %s -jar $0 \"$@\"\n"
               "@echo off\r\njava %s -jar %%1 \"%%~f0\" %%*\r\ngoto :eof\r\n")
          flags flags))

(defn boot-preamble [flags main]
  (format (str ":;exec java %s -Xbootclasspath/a:$0 %s \"$@\"\n"
               "@echo off\r\njava %s -Xbootclasspath/a:%%1 %s "
               "\"%%~f0\" %%*\r\ngoto :eof\r\n")
          flags main flags main))

(defn write-jar-preamble! [out flags]
  (.write out (.getBytes (jar-preamble flags))))

(defn write-boot-preamble! [out flags main]
  (.write out (.getBytes (boot-preamble flags main))))

(defn ^:private copy-bin [project binfile]
  (when-let [bin-path (get-in project [:bin :bin-path])]
    (let [bin-path (clojure.string/replace
                    bin-path
                    "$HOME"
                    (System/getProperty "user.home"))
          new-binfile (fs/file bin-path (fs/base-name binfile))]
      (println "Copying binary to" bin-path)
      (fs/chmod "+x" (fs/copy+ binfile new-binfile)))))

(defn bin
  "Create a standalone console executable for your project.

Add :main to your project.clj to specify the namespace that contains your
-main function."
  [project]
  (if (:main project)
    (let [opts (jvm-options project)
          target (fs/file (:target-path project))
          binfile (fs/file target
                           (or (get-in project [:bin :name])
                               (str (:name project) "-" (:version project))))]
      (uberjar project)
      (println "Creating standalone executable:" (str binfile))
      (with-open [bin (FileOutputStream. binfile)]
        (if (get-in project [:bin :bootclasspath])
          (write-boot-preamble! bin opts (:main project))
          (write-jar-preamble! bin opts))
        (io/copy (fs/file (get-jar-filename project :uberjar)) bin))
      (fs/chmod "+x" binfile)
      (copy-bin project binfile))
    (println "Cannot create bin without :main namespace in project.clj")))
