(ns githubbing.exports.xlsx
  (:require [dk.ative.docjure.spreadsheet :refer :all]      ; see https://github.com/mjul/docjure
            [clojure.string :as string])
  (:import (java.io FileNotFoundException)))

;;;;;;;;;;;;;;;;;;
;; CSV formatting

(defn- format-xlsx-row
  "Form a sequence of values in keyseq order, replacing nils with \"\""
  [keyseq m]
  (map (fnil str "")
       (map m keyseq)))

(defn- xlsx-rows
  "Given a report, return rows prepared for adding to the xlsx sheet"
  [r]
  (let [labels (map first (:headings r))
        keyseq (map second (:headings r))]
    (cons labels
          (map (partial format-xlsx-row keyseq) (:data r)))))

(defn- open-workbook
  "Open this workbook, or create a new one with an empty \"Overview\" sheet."
  [file]
  (try (load-workbook file)
       (catch FileNotFoundException _
         (create-workbook "Overview" nil))))

(defn- new-sheet!
  "Create a new sheet with this name and return it. If it already exists, erase it first."
  [wb sheet-name]
  (if-let [sheet (select-sheet sheet-name wb)]
    (remove-all-rows! sheet)
    (add-sheet! wb sheet-name)))

(defn- create-sheet-name [title]
  (let [sheet-name (string/replace title "/" "_")]
    (if (> (count sheet-name) 30)
      (format "%.27s" sheet-name)
      sheet-name)))

(defn export
  "Create a Microsoft Excel file with header row and a row for each line of data"
  ([report] (export report (format "reports/%s.xlsx" (:name report))))
  ([report outfile-name]
   (let [wb (open-workbook outfile-name)
         sheet (new-sheet! wb (create-sheet-name (:name report)))]
     (add-rows! sheet (xlsx-rows report))
     (save-workbook! outfile-name wb))))
