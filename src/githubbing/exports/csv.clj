(ns githubbing.exports.csv
  "Export reports to CSV files"
  (:require [clojure.java.io :as io]))

(defn- print-tabbed-data [c]
  (println (apply str (interpose "\t" c))))

(defn- print-output
  "Print to stdout"
  [report]
  (let [labels (map first (:headings report))
        keyseq (map second (:headings report))]
    (print-tabbed-data labels)
    (doseq [row (:data report)]
      (print-tabbed-data (map row keyseq)))))

(defn export
  "Create a tab-delimited CSV file with header row and a row for each line of data"
  ([report] (export report (format "reports/%s.txt" (:name report))))
  ([report outfile-name]
   (with-open [w (io/writer outfile-name)]
     (binding [*out* w]
       (print-output report)))))

(def preview
  "See the CSV content printed to std out"
  print-output)
