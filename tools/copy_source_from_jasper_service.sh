#!/usr/bin/env bash
if [ -z "$1" ]; then
    echo "Usage: $0 PATH_TO_JASPER_SERVICE"
    exit 1
fi

JASPER_SERVICE="$1"

function copy_source_file {
    local path="buildSrc/$1"
    local source="$JASPER_SERVICE/jasper-service-server/$1"
    if [ -d "$source" ]; then
        mkdir -p "$path"
        cp -r "$source"/* "$path"
    else
        mkdir -p "$(dirname "$path")"
        cp -r "$source" "$path"
    fi
    echo Copied "$1"
}

copy_source_file src/main/scala/com/riege/jasperservice/frontend/JasperServiceProtocol.scala
copy_source_file src/main/scala/com/riege/jasperservice/backend/BackendException.scala
copy_source_file src/main/scala/com/riege/jasperservice/backend/PrintException.scala
copy_source_file src/main/scala/com/riege/jasperservice/backend/FormsLoader.scala
copy_source_file src/main/scala/com/riege/jasperservice/backend/PDFProducer.scala
copy_source_file src/main/scala/com/riege/jasperservice/backend/PDFUtils.scala
copy_source_file src/main/scala/com/riege/jasperservice/backend/TextProducer.scala
copy_source_file src/main/scala/com/riege/jasperservice/model/Report.scala
copy_source_file src/main/scala/com/riege/jasperservice/model/Simulations.scala
copy_source_file src/main/scala/com/riege/jasperservice/model/PageDimension.scala
copy_source_file src/main/scala/com/riege/jasperservice/model/TextDocument.scala
copy_source_file src/main/scala/com/riege/jasperservice/model/Image.scala
copy_source_file src/main/scala/com/riege/jasperservice/model/PDFDocument.scala
copy_source_file src/main/scala/com/riege/jasperservice/model/TextRawData.scala
copy_source_file src/main/scala/com/riege/jasperservice/model/PDFRawData.scala
copy_source_file src/main/scala/com/riege/jasperservice/model/LabelDocument.scala
copy_source_file src/main/scala/com/riege/jasperservice/model/UploadedImage.scala
copy_source_file src/main/scala/com/riege/jasperservice/model/LabelRawData.scala
copy_source_file src/main/scala/com/riege/jasperservice/model/DocumentContext.scala
copy_source_file src/main/scala/com/riege/jasperservice/FileUtils.scala
