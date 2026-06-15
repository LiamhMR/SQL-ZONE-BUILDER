package com.seminario.plugin.sql;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import com.seminario.plugin.model.SQLDifficulty;

/**
 * Bank of predefined SQL challenges organized by difficulty level
 * Updated to use new database structure with _pk and _fk naming conventions
 */
public class SQLChallengeBank {
    
    private static final Random random = new Random();
    
    /**
     * Challenge data structure
     */
    public static class Challenge {
        private final String description;
        private final String expectedQuery;
        private final String hint1;
        private final String hint2;
        private final String hint3;
        
        public Challenge(String description, String expectedQuery, String hint1, String hint2, String hint3) {
            this.description = description;
            this.expectedQuery = expectedQuery;
            this.hint1 = hint1;
            this.hint2 = hint2;
            this.hint3 = hint3;
        }
        
        public String getDescription() { return description; }
        public String getExpectedQuery() { return expectedQuery; }
        public String getHint1() { return hint1; }
        public String getHint2() { return hint2; }
        public String getHint3() { return hint3; }
    }
    
    // ===== LEVEL 1: BASIC =====
    private static final List<Challenge> BASIC_CHALLENGES = Arrays.asList(
        new Challenge(
            "Muestra los 'nombres' de todos los jugadores del mundo 'overworld'",
            "SELECT nombre FROM Jugadores WHERE mundo = 'overworld'",
            "Usa SELECT nombre FROM Jugadores",
            "Filtra por mundo con WHERE",
            "Usa comillas simples para el texto: 'overworld'"
        ),
        new Challenge(
            "Lista los nombres de todos los jugadores que tienen nivel mayor a 20",
            "SELECT nombre FROM Jugadores WHERE nivel > 20",
            "SELECT nombre FROM la tabla Jugadores",
            "WHERE nivel > [número]",
            "El número es 20"
        ),
        new Challenge(
            "Encuentra los nombres de jugadores que tienen más de 50 esmeraldas",
            "SELECT nombre FROM Jugadores WHERE esmeraldas > 50",
            "Consulta la tabla Jugadores",
            "Filtra por la columna esmeraldas",
            "Usa el operador > para 'mayor que'"
        ),
        new Challenge(
            "Muestra todos los 'items' del inventario que son 'épico'",
            "SELECT item FROM Inventarios WHERE rareza = 'épico'",
            "SELECT item FROM Inventarios",
            "WHERE rareza = [valor]",
            "El valor es 'épico' entre comillas"
        ),
        new Challenge(
            "Lista los nombres de todos los jugadores que tienen menos de 30 oro",
            "SELECT nombre FROM Jugadores WHERE oro < 30",
            "Usa SELECT nombre FROM Jugadores",
            "WHERE oro < [número]",
            "El número es 30"
        ),
        new Challenge(
            "Encuentra los 'nombres' de jugadores que tienen exactamente 25 nivel",
            "SELECT nombre FROM Jugadores WHERE nivel = 25",
            "Usa el operador = para igualdad exacta",
            "WHERE nivel = [número]",
            "El número es 25"
        ),
        new Challenge(
            "Muestra los 'items' del inventario que NO son raros",
            "SELECT item FROM Inventarios WHERE rareza != 'raro'",
            "Usa != para 'no igual'",
            "WHERE rareza != [valor]",
            "El valor es 'raro' entre comillas"
        ),
        new Challenge(
            "Lista los nombres de jugadores del mundo 'nether' o 'end'",
            "SELECT nombre FROM Jugadores WHERE mundo = 'nether' OR mundo = 'end'",
            "Usa OR para múltiples condiciones",
            "WHERE mundo = 'nether' OR mundo = 'end'",
            "Usa comillas simples para ambos valores"
        )
    );
    
    // ===== LEVEL 2: INTERMEDIATE =====
    private static final List<Challenge> INTERMEDIATE_CHALLENGES = Arrays.asList(
        new Challenge(
            "Lista los 'nombres' de jugadores ordenados por cantidad de 'diamantes' (mayor a menor)",
            "SELECT nombre, diamantes FROM Jugadores ORDER BY diamantes DESC",
            "Usa ORDER BY para ordenar",
            "DESC significa descendente (mayor a menor)",
            "Incluye tanto nombre como diamantes en SELECT"
        ),
        new Challenge(
            "Encuentra los 'nombres' de los 3 jugadores con más 'oro'",
            "SELECT nombre, oro FROM Jugadores ORDER BY oro DESC LIMIT 3",
            "ORDER BY oro DESC para ordenar por oro",
            "LIMIT [número] para limitar resultados",
            "El número es 3"
        ),
        new Challenge(
            "Muestra los 'nombres' de jugadores que tienen entre 10 y 200 'diamantes'",
            "SELECT nombre, diamantes FROM Jugadores WHERE diamantes BETWEEN 10 AND 200",
            "Usa BETWEEN para rangos",
            "La sintaxis es: BETWEEN valor1 AND valor2",
            "Los valores son 10 y 200"
        ),
        new Challenge(
            "Lista 'items' encantados del inventario ordenados por 'cantidad'",
            "SELECT item, cantidad FROM Inventarios WHERE encantado = true ORDER BY cantidad DESC",
            "WHERE encantado = true para items encantados",
            "ORDER BY cantidad DESC para ordenar",
            "true se escribe sin comillas"
        ),
        new Challenge(
            "Encuentra jugadores cuyos nombres empiecen con 'A' o 'S'",
            "SELECT nombre FROM Jugadores WHERE nombre LIKE 'A%' OR nombre LIKE 'S%'",
            "Usa LIKE con % para patrones",
            "% significa 'cualquier texto después'",
            "Usa OR para múltiples condiciones"
        ),
        new Challenge(
            "Muestra los 5 jugadores con menos esmeraldas. (Lista nombres y esmeraldas)",
            "SELECT nombre, esmeraldas FROM Jugadores ORDER BY esmeraldas ASC LIMIT 5",
            "ORDER BY esmeraldas ASC para ordenar ascendente",
            "LIMIT 5 para los primeros 5 resultados",
            "ASC significa ascendente (menor a mayor)"
        ),
        new Challenge(
            "Lista jugadores con nivel entre 15 y 30. (Incluye nombre y nivel)",
            "SELECT nombre, nivel FROM Jugadores WHERE nivel BETWEEN 15 AND 30",
            "BETWEEN incluye los valores límite",
            "WHERE nivel BETWEEN 15 AND 30",
            "Incluye nombre y nivel en SELECT"
        ),
        new Challenge(
            "Encuentra 'items' que contengan la palabra 'espada' ",
            "SELECT item FROM Inventarios WHERE item LIKE '%espada%'",
            "LIKE '%espada%' busca la palabra en cualquier parte",
            "% antes y después busca en cualquier posición",
            "SELECT item FROM Inventarios"
        )
    );
    
    // ===== LEVEL 3: ADVANCED =====
    private static final List<Challenge> ADVANCED_CHALLENGES = Arrays.asList(
        new Challenge(
            "Cuenta cuántos jugadores hay en cada mundo",
            "SELECT mundo, COUNT(*) FROM Jugadores GROUP BY mundo",
            "Usa COUNT(*) para contar filas",
            "GROUP BY mundo agrupa por mundo",
            "SELECT debe incluir la columna del GROUP BY"
        ),
        new Challenge(
            "Muestra el total de diamantes por mundo",
            "SELECT mundo, SUM(diamantes) FROM Jugadores GROUP BY mundo",
            "SUM(diamantes) suma todos los diamantes",
            "GROUP BY mundo agrupa por mundo",
            "SELECT mundo, SUM(diamantes)"
        ),
        new Challenge(
            "Lista los tipos de construcción y cuántas hay de cada tipo",
            "SELECT tipo, COUNT(*) FROM Construcciones GROUP BY tipo",
            "COUNT(*) cuenta las construcciones",
            "GROUP BY tipo agrupa por tipo",
            "SELECT tipo, COUNT(*)"
        ),
        new Challenge(
            "Encuentra el promedio de nivel de los jugadores por mundo",
            "SELECT mundo, AVG(nivel) FROM Jugadores GROUP BY mundo",
            "AVG(nivel) calcula el promedio",
            "GROUP BY mundo agrupa por mundo",
            "SELECT mundo, AVG(nivel)"
        ),
        new Challenge(
            "Muestra mundos que tienen más de 2 jugadores",
            "SELECT mundo, COUNT(*) FROM Jugadores GROUP BY mundo HAVING COUNT(*) > 2",
            "GROUP BY mundo para agrupar",
            "HAVING se usa después de GROUP BY",
            "HAVING COUNT(*) > 2 filtra grupos"
        ),
        new Challenge(
            "Calcula el total de oro por mundo, ordenado de mayor a menor",
            "SELECT mundo, SUM(oro) FROM Jugadores GROUP BY mundo ORDER BY SUM(oro) DESC",
            "SUM(oro) suma el oro por mundo",
            "ORDER BY SUM(oro) DESC ordena por suma descendente",
            "Combina GROUP BY con ORDER BY"
        ),
        new Challenge(
            "Encuentra el promedio de esmeraldas por mundo donde hay más de 1 jugador",
            "SELECT mundo, AVG(esmeraldas) FROM Jugadores GROUP BY mundo HAVING COUNT(*) > 1",
            "AVG(esmeraldas) calcula el promedio",
            "HAVING COUNT(*) > 1 filtra mundos con más de 1 jugador",
            "GROUP BY mundo para agrupar"
        ),
        new Challenge(
            "Muestra la cantidad máxima de diamantes por mundo",
            "SELECT mundo, MAX(diamantes) FROM Jugadores GROUP BY mundo",
            "MAX(diamantes) encuentra el valor máximo",
            "GROUP BY mundo agrupa por mundo",
            "SELECT mundo, MAX(diamantes)"
        )
    );
    
    // ===== LEVEL 4: EXPERT =====
    private static final List<Challenge> EXPERT_CHALLENGES = Arrays.asList(
        new Challenge(
            "Lista jugadores con sus construcciones (nombre del jugador y nombre de construcción)",
            "SELECT j.nombre, c.nombre FROM Jugadores j INNER JOIN Construcciones c ON j.jugador_pk = c.jugador_fk",
            "Usa INNER JOIN para conectar tablas",
            "ON j.jugador_pk = c.jugador_fk relaciona las claves",
            "j y c son alias para las tablas"
        ),
        new Challenge(
            "Encuentra jugadores que tienen al menos una construcción tipo 'castillo'",
            "SELECT DISTINCT j.nombre FROM Jugadores j INNER JOIN Construcciones c ON j.jugador_pk = c.jugador_fk WHERE c.tipo = 'castillo'",
            "INNER JOIN conecta Jugadores y Construcciones",
            "WHERE c.tipo = 'castillo' filtra por tipo",
            "DISTINCT evita duplicados"
        ),
        new Challenge(
            "Muestra jugadores con la cantidad total de bloques usados en sus construcciones",
            "SELECT j.nombre, SUM(c.bloques_usados) FROM Jugadores j INNER JOIN Construcciones c ON j.jugador_pk = c.jugador_fk GROUP BY j.nombre",
            "INNER JOIN conecta las tablas por las claves",
            "SUM(c.bloques_usados) suma los bloques",
            "GROUP BY j.nombre agrupa por jugador"
        ),
        new Challenge(
            "Lista jugadores que han obtenido logros de la categoría 'construcción'",
            "SELECT DISTINCT j.nombre FROM Jugadores j INNER JOIN Logros l ON j.jugador_pk = l.jugador_fk WHERE l.categoria = 'construcción'",
            "JOIN Jugadores con Logros usando claves PK-FK",
            "WHERE l.categoria = 'construcción'",
            "DISTINCT elimina duplicados"
        ),
        new Challenge(
            "Encuentra el jugador que más ha comerciado (como vendedor)",
            "SELECT j.nombre, COUNT(*) as ventas FROM Jugadores j INNER JOIN Comercio c ON j.jugador_pk = c.vendedor_fk GROUP BY j.nombre ORDER BY ventas DESC LIMIT 1",
            "JOIN Jugadores con Comercio por vendedor_fk",
            "GROUP BY j.nombre y COUNT(*)",
            "ORDER BY ventas DESC LIMIT 1"
        ),
        new Challenge(
            "Lista jugadores con sus items del inventario (nombre jugador y item)",
            "SELECT j.nombre, i.item FROM Jugadores j INNER JOIN Inventarios i ON j.jugador_pk = i.jugador_fk",
            "JOIN Jugadores con Inventarios usando claves",
            "ON j.jugador_pk = i.jugador_fk relaciona las tablas",
            "SELECT j.nombre, i.item"
        ),
        new Challenge(
            "Encuentra jugadores que han hecho más de 2 transacciones como compradores",
            "SELECT j.nombre, COUNT(*) as compras FROM Jugadores j INNER JOIN Comercio c ON j.jugador_pk = c.comprador_fk GROUP BY j.nombre HAVING COUNT(*) > 2",
            "JOIN con Comercio usando comprador_fk",
            "GROUP BY j.nombre y COUNT(*)",
            "HAVING COUNT(*) > 2 filtra por más de 2"
        ),
        new Challenge(
            "Muestra construcciones con el nombre de su dueño y total de bloques",
            "SELECT j.nombre, c.nombre, c.bloques_usados FROM Jugadores j INNER JOIN Construcciones c ON j.jugador_pk = c.jugador_fk ORDER BY c.bloques_usados DESC",
            "JOIN Jugadores con Construcciones usando claves",
            "SELECT nombre jugador, nombre construcción, bloques",
            "ORDER BY c.bloques_usados DESC para ordenar"
        )
    );
    
    // ===== LEVEL 5: MASTER =====
    private static final List<Challenge> MASTER_CHALLENGES = Arrays.asList(
        new Challenge(
            "Lista jugadores que tienen construcciones pero nunca han comerciado",
            "SELECT DISTINCT j.nombre FROM Jugadores j INNER JOIN Construcciones c ON j.jugador_pk = c.jugador_fk WHERE j.jugador_pk NOT IN (SELECT DISTINCT vendedor_fk FROM Comercio UNION SELECT DISTINCT comprador_fk FROM Comercio)",
            "INNER JOIN para jugadores con construcciones",
            "NOT IN con subconsulta para excluir comerciantes",
            "UNION combina vendedores y compradores"
        ),
        new Challenge(
            "Encuentra jugadores que tienen más diamantes que el promedio",
            "SELECT nombre, diamantes FROM Jugadores WHERE diamantes > (SELECT AVG(diamantes) FROM Jugadores)",
            "Subconsulta: (SELECT AVG(diamantes) FROM Jugadores)",
            "WHERE diamantes > [subconsulta]",
            "La subconsulta calcula el promedio"
        ),
        new Challenge(
            "Muestra el ranking de jugadores por puntos totales de logros",
            "SELECT j.nombre, COALESCE(SUM(l.puntos), 0) as puntos_totales FROM Jugadores j LEFT JOIN Logros l ON j.jugador_pk = l.jugador_fk GROUP BY j.nombre ORDER BY puntos_totales DESC",
            "LEFT JOIN incluye jugadores sin logros",
            "COALESCE(SUM(l.puntos), 0) maneja valores NULL",
            "ORDER BY puntos_totales DESC para ranking"
        ),
        new Challenge(
            "Lista jugadores con más construcciones que el promedio de construcciones por jugador",
            "SELECT j.nombre, COUNT(c.construccion_pk) as construcciones FROM Jugadores j LEFT JOIN Construcciones c ON j.jugador_pk = c.jugador_fk GROUP BY j.nombre HAVING COUNT(c.construccion_pk) > (SELECT AVG(construcciones_por_jugador) FROM (SELECT COUNT(*) as construcciones_por_jugador FROM Construcciones GROUP BY jugador_fk) as subconsulta)",
            "LEFT JOIN para incluir todos los jugadores",
            "HAVING con subconsulta compleja",
            "Subconsulta calcula promedio de construcciones"
        ),
        new Challenge(
            "Encuentra pares de jugadores que han comerciado entre sí (intercambio mutuo)",
            "SELECT DISTINCT j1.nombre as jugador1, j2.nombre as jugador2 FROM Comercio c1 INNER JOIN Comercio c2 ON c1.vendedor_fk = c2.comprador_fk AND c1.comprador_fk = c2.vendedor_fk INNER JOIN Jugadores j1 ON c1.vendedor_fk = j1.jugador_pk INNER JOIN Jugadores j2 ON c1.comprador_fk = j2.jugador_pk WHERE j1.jugador_pk < j2.jugador_pk",
            "Doble JOIN en Comercio (c1 y c2)",
            "Condiciones cruzadas: vendedor = comprador",
            "WHERE j1.jugador_pk < j2.jugador_pk evita duplicados"
        ),
        new Challenge(
            "Lista jugadores que tienen más oro que cualquier jugador del mundo 'nether'",
            "SELECT nombre, oro FROM Jugadores WHERE oro > (SELECT MAX(oro) FROM Jugadores WHERE mundo = 'nether')",
            "Subconsulta con MAX para encontrar el máximo oro en nether",
            "WHERE oro > [subconsulta con MAX]",
            "Compara con el valor máximo de otro grupo"
        ),
        new Challenge(
            "Encuentra jugadores con items únicos (que nadie más tiene)",
            "SELECT DISTINCT j.nombre, i.item FROM Jugadores j INNER JOIN Inventarios i ON j.jugador_pk = i.jugador_fk WHERE i.item IN (SELECT item FROM Inventarios GROUP BY item HAVING COUNT(DISTINCT jugador_fk) = 1)",
            "Subconsulta que encuentra items únicos",
            "HAVING COUNT(DISTINCT jugador_fk) = 1",
            "Items que solo tiene un jugador"
        ),
        new Challenge(
            "Muestra el top 3 de mundos por riqueza total (suma de oro + diamantes + esmeraldas)",
            "SELECT mundo, SUM(oro + diamantes + esmeraldas) as riqueza_total FROM Jugadores GROUP BY mundo ORDER BY riqueza_total DESC LIMIT 3",
            "Suma múltiples columnas: oro + diamantes + esmeraldas",
            "GROUP BY mundo para agrupar por mundo",
            "ORDER BY riqueza_total DESC LIMIT 3"
        )
    );
    
    /**
     * Get a random challenge for the specified difficulty
     * @param difficulty The SQL difficulty level
     * @return A random challenge for that difficulty
     */
    public static Challenge getRandomChallenge(SQLDifficulty difficulty) {
        List<Challenge> challenges = switch (difficulty) {
            case BASIC -> BASIC_CHALLENGES;
            case INTERMEDIATE -> INTERMEDIATE_CHALLENGES;
            case ADVANCED -> ADVANCED_CHALLENGES;
            case EXPERT -> EXPERT_CHALLENGES;
            case MASTER -> MASTER_CHALLENGES;
        };
        
        return challenges.get(random.nextInt(challenges.size()));
    }
    
    /**
     * Get all challenges for a specific difficulty
     * @param difficulty The SQL difficulty level
     * @return List of all challenges for that difficulty
     */
    public static List<Challenge> getAllChallenges(SQLDifficulty difficulty) {
        return switch (difficulty) {
            case BASIC -> BASIC_CHALLENGES;
            case INTERMEDIATE -> INTERMEDIATE_CHALLENGES;
            case ADVANCED -> ADVANCED_CHALLENGES;
            case EXPERT -> EXPERT_CHALLENGES;
            case MASTER -> MASTER_CHALLENGES;
        };
    }
    
    /**
     * Get total number of challenges available
     * @return Total challenge count
     */
    public static int getTotalChallenges() {
        return BASIC_CHALLENGES.size() + 
               INTERMEDIATE_CHALLENGES.size() + 
               ADVANCED_CHALLENGES.size() + 
               EXPERT_CHALLENGES.size() + 
               MASTER_CHALLENGES.size();
    }
    
    /**
     * Get challenge count for a specific difficulty
     * @param difficulty The difficulty level
     * @return Number of challenges for that difficulty
     */
    public static int getChallengeCount(SQLDifficulty difficulty) {
        return getAllChallenges(difficulty).size();
    }
}