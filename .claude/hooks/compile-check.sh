#!/usr/bin/env bash
# Hook Stop: antes de o Claude encerrar o turno, garante que main + test compilam.
# - Sem alteração em .java/pom.xml no working tree: sai na hora (não roda Maven).
# - Compilação falhou: devolve os erros para a sessão corrigir ({"decision":"block"}).
# - Proteção contra loop: no máximo 3 bloqueios seguidos por sessão; depois libera
#   (a falha aparece como erro de hook) para não prender a sessão num erro que o Claude não resolve.
# Requer: jq, git.
set -uo pipefail

MAX_BLOQUEIOS=3

command -v jq >/dev/null 2>&1 || { echo "compile-check.sh: jq não encontrado; verificação ignorada." >&2; exit 0; }

input=$(cat)
root="${CLAUDE_PROJECT_DIR:-$(pwd)}"
cd "$root" || exit 0

session=$(jq -r '.session_id // "sem-sessao"' <<<"$input" | tr -cd '[:alnum:]_-')
stop_hook_active=$(jq -r '.stop_hook_active // false' <<<"$input")
contador_dir="target/.claude-hooks"
contador="$contador_dir/stop-$session"

if [ -z "$(git status --porcelain -- '*.java' pom.xml 2>/dev/null)" ]; then
    rm -f "$contador"
    exit 0
fi

if out=$(./mvnw -q test-compile 2>&1); then
    rm -f "$contador"
    exit 0
fi

# Falhou: conta bloqueios seguidos (zera quando a sessão não veio de um bloqueio anterior).
mkdir -p "$contador_dir"
if [ "$stop_hook_active" = "true" ]; then
    n=$(( $(cat "$contador" 2>/dev/null || echo 0) + 1 ))
else
    n=1
fi
echo "$n" >"$contador"

erros=$(grep -E '^\[ERROR\]' <<<"$out" | grep -v -E 'Help 1|-> \[Help|^\[ERROR\] *$|Re-run Maven|For more information|full stack trace|enable full debug' | head -40)
[ -n "$erros" ] || erros=$(tail -40 <<<"$out")

if [ "$n" -gt "$MAX_BLOQUEIOS" ]; then
    rm -f "$contador"
    echo "compile-check: ./mvnw -q test-compile ainda falha após $MAX_BLOQUEIOS tentativas; encerrando mesmo assim." >&2
    echo "$erros" >&2
    exit 1
fi

jq -n --arg erros "$erros" --arg n "$n" --arg max "$MAX_BLOQUEIOS" '{
  decision: "block",
  reason: ("O código não compila (./mvnw -q test-compile, tentativa " + $n + "/" + $max + "). Corrija antes de encerrar:\n" + $erros)
}'
exit 0
