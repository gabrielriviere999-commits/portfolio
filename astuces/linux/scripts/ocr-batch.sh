#!/bin/bash

# Dossier contenant les PDF à convertir
INPUT_DIR="./pdf_input"
# Dossier de sortie
OUTPUT_DIR="./pdf_output"

# Crée les dossiers si nécessaires
mkdir -p "$INPUT_DIR"
mkdir -p "$OUTPUT_DIR"

# Langue OCR (modifiable)
LANG="fra"

# Traitement de chaque fichier PDF
for file in "$INPUT_DIR"/*.pdf; do
    filename=$(basename "$file")
    output_file="$OUTPUT_DIR/$filename"
    
    echo "Conversion OCR : $filename"
    
    ocrmypdf --force-ocr --rotate-pages --deskew -l "$LANG" "$file" "$output_file"
    
    echo "Fichier traité : $output_file"
done

echo "Tous les fichiers PDF ont été convertis avec OCR."
