import os, re

# Fonction pour enlever les balises HTML et le CSS inline
def strip_tags(html):
    # Supprimer les balises <style> et leur contenu
    html = re.sub(r'<style[^>]*>.*?</style>', '', html, flags=re.DOTALL | re.IGNORECASE)
    # Supprimer les balises <script> et leur contenu
    html = re.sub(r'<script[^>]*>.*?</script>', '', html, flags=re.DOTALL | re.IGNORECASE)
    # Supprimer les balises HTML classiques
    return re.sub(r'<[^>]*>', '', html)

folder = "."
output_file = "docs.js"

TEXT_EXTENSIONS = (".html", ".txt")
IGNORE_FILES = {output_file, "liste-fichiers.html"}

docs = []

for root, dirs, files in os.walk(folder):
    # Retirer les dossiers inutiles de la liste parcourue
    dirs[:] = [d for d in dirs if d not in {".git", ".github", "__pycache__"}]

    for f in files:
        # Ignorer certains fichiers générés
        if f in IGNORE_FILES:
            continue

        path = os.path.join(root, f)
        content = ""

        # Si c'est un fichier texte → on extrait le contenu
        if f.endswith(TEXT_EXTENSIONS):
            try:
                if f.endswith(".html"):
                    with open(path, encoding="utf-8") as file:
                        content = file.read()
                    content = strip_tags(content)

                elif f.endswith(".txt"):
                    with open(path, encoding="utf-8") as file:
                        content = file.read()

            except Exception as e:
                print("Erreur lecture", path, e)
                content = ""

        else:
            content = ""

        # Nettoyage du contenu pour éviter les erreurs JS
        safe_content = (
            f + " " + content
        ).replace("\n", " ") \
         .replace("\\", "\\\\") \
         .replace('"', '\\"')

        docs.append({
            "title": f,
            "url": os.path.relpath(path, folder),
            "content": safe_content
        })

docs.sort(key=lambda d: d["title"].lower())
# Écriture dans docs.js
with open(output_file, "w", encoding="utf-8") as out:
    out.write("var docs = [\n")
    for i, d in enumerate(docs):
        comma = "," if i < len(docs)-1 else ""
        out.write("  {title:\"%s\", url:\"%s\", content:\"%s\"}%s\n" %
                  (d["title"], d["url"], d["content"], comma))
    out.write("];\n")

print("Fichier docs.js généré avec succès !")
