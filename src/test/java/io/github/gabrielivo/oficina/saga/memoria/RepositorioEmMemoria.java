package io.github.gabrielivo.oficina.saga.memoria;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Implementação em memória de um {@code JpaRepository} para os testes da Saga sem Spring nem banco (Etapa 5.4). Atende
 * os métodos CRUD usados pelos serviços ({@code save}, {@code saveAndFlush}, {@code findById}, {@code existsById},
 * {@code findAll}, {@code count}, {@code delete*}) e, para as consultas derivadas, executa a função registrada com o
 * nome do método. Qualquer outro método falha de forma explícita, para o teste não passar por acaso.
 *
 * <p>Os registros ficam na ordem de inserção, que é a ordem de gravação usada pelo outbox.
 *
 * @param <T> tipo da entidade
 */
public final class RepositorioEmMemoria<T> {

    private final Map<Object, T> registros = new LinkedHashMap<>();
    private final Function<T, Object> id;
    private final Map<String, BiFunction<List<T>, Object[], Object>> consultas = new HashMap<>();

    private RepositorioEmMemoria(Function<T, Object> id) {
        this.id = id;
    }

    /** Cria um repositório cujas entidades expõem o id por {@code getId()}. */
    public static <T> RepositorioEmMemoria<T> porGetId() {
        return new RepositorioEmMemoria<>(RepositorioEmMemoria::lerId);
    }

    /** Cria um repositório com a função que extrai o id da entidade. */
    public static <T> RepositorioEmMemoria<T> porId(Function<T, Object> id) {
        return new RepositorioEmMemoria<>(id);
    }

    /**
     * Registra a implementação de uma consulta derivada.
     *
     * @param metodo nome do método da interface do repositório
     * @param consulta recebe os registros (na ordem de inserção) e os argumentos da chamada
     */
    public RepositorioEmMemoria<T> comConsulta(String metodo, BiFunction<List<T>, Object[], Object> consulta) {
        consultas.put(metodo, consulta);
        return this;
    }

    public List<T> registros() {
        return List.copyOf(registros.values());
    }

    /** Devolve um proxy que implementa a interface do repositório sobre este armazenamento. */
    public <R> R como(Class<R> interfaceRepositorio) {
        InvocationHandler handler =
                (proxy, method, args) -> invocar(proxy, method, args == null ? new Object[0] : args);
        return interfaceRepositorio.cast(Proxy.newProxyInstance(
                interfaceRepositorio.getClassLoader(), new Class<?>[] {interfaceRepositorio}, handler));
    }

    @SuppressWarnings("unchecked")
    private Object invocar(Object proxy, Method method, Object[] args) {
        String nome = method.getName();
        BiFunction<List<T>, Object[], Object> consulta = consultas.get(nome);
        if (consulta != null) {
            return consulta.apply(new ArrayList<>(registros.values()), args);
        }
        return switch (nome) {
            case "save", "saveAndFlush" -> {
                T entidade = (T) args[0];
                registros.put(id.apply(entidade), entidade);
                yield entidade;
            }
            case "findById" -> Optional.ofNullable(registros.get(args[0]));
            case "existsById" -> registros.containsKey(args[0]);
            case "findAll" -> {
                if (args.length > 0) {
                    throw naoSuportado(method);
                }
                yield new ArrayList<>(registros.values());
            }
            case "count" -> (long) registros.size();
            case "deleteById" -> registros.remove(args[0]);
            case "delete" -> registros.remove(id.apply((T) args[0]));
            case "deleteAll" -> {
                if (args.length > 0) {
                    throw naoSuportado(method);
                }
                registros.clear();
                yield null;
            }
            case "flush" -> null;
            case "toString" -> "RepositorioEmMemoria(" + registros.size() + ")";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> throw naoSuportado(method);
        };
    }

    private static UnsupportedOperationException naoSuportado(Method method) {
        return new UnsupportedOperationException("Método não suportado pelo repositório em memória: " + method.getName()
                + ". Registre-o com comConsulta(...).");
    }

    private static Object lerId(Object entidade) {
        try {
            return entidade.getClass().getMethod("getId").invoke(entidade);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Entidade sem getId(): " + entidade.getClass().getName(), e);
        }
    }
}
