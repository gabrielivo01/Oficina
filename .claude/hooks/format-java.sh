#!/usr/bin/env bash
# Hook PostToolUse (Edit|Write): formata com Spotless o .java que o Claude acabou de editar.
# Usa a mesma configuração do pom.xml (palantir-java-format + removeUnusedImports), então o
# resultado é idêntico ao do `./mvnw spotless:check` que roda no verify. ~2 s por arquivo.
# Requer: jq.
set -uo pipefail

command -v jq >/dev/null 2>&1 || { echo "format-java.sh: jq não encontrado; formatação ignorada." >&2; exit 0; }

input=$(cat)
file=$(jq -r '.tool_input.file_path // empty' <<<"$input")
root="${CLAUDE_PROJECT_DIR:-$(pwd)}"

# Só arquivos .java dentro de src/ (o que o Spotless cobre); o resto sai na hora.
case "$file" in
    "$root"/src/*.java) ;;
    *) exit 0 ;;
esac
[ -f "$file" ] || exit 0

# spotlessFiles recebe uma regex: escapa os metacaracteres do caminho absoluto.
regex=$(printf '%s' "$file" | sed 's/[][\.*^$()+?{}|]/\\&/g')

cd "$root" || exit 0
if ! out=$(./mvnw -q spotless:apply -DspotlessFiles="$regex" 2>&1); then
    # exit 2 em PostToolUse não bloqueia: só mostra o stderr ao Claude (o arquivo provavelmente não compila).
    echo "Spotless não conseguiu formatar ${file#"$root"/} (provável erro de sintaxe):" >&2
    grep -E '^\[ERROR\]' <<<"$out" \
        | grep -v -E 'Help 1|-> \[Help|^\[ERROR\] *$|Re-run Maven|For more information|full stack trace|enable full debug' \
        | head -15 >&2
    exit 2
fi
exit 0
