package graphql;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * Following the relay specification, together with the requested information (edges)
 * we are going to receive a pageInfo that contains cursor information for the pagination.
 *
 * Result set example:
 *  "edges": [
 *         {
 *           "node": {
 *             "TotalCost": 1.5,
 *             "Status": "delivered",
 *             "OrderId": "ORD-1023",
 *             "OrderDate": null,
 *             "id": "Postgres_MyOrder-ORD\\-1023"
 *           }
 *         },
 *         {
 *           "node": {
 *             "TotalCost": 1.5,
 *             "Status": "delivered",
 *             "OrderId": "ORD-1024",
 *             "OrderDate": null,
 *             "id": "Postgres_MyOrder-ORD\\-1024"
 *           }
 *         }
 *       ],
 *       "pageInfo": {
 *         "hasNextPage": true,
 *         "endCursor": "ZW5kQ3Vyc29y"
 *       }
 *     }
 *   }
 */
public class QueryResultSet {
    private List<LinkedHashMap<String, Object>> edges;
    private Map<String, Object> pageInfo;

    /**
     * @param edges List of nodes, where each node is a map from columnName to value
     * @param pageInfo pagination info map, including hasNextPage (boolean) and endCursor (String)
     */
    public QueryResultSet(List<LinkedHashMap<String, Object>> edges, Map<String, Object> pageInfo) {
        this.edges = edges;
        this.pageInfo = pageInfo;
    }

    /**
     * @return pageInfo pagination info map, including hasNextPage (boolean) and endCursor (String)
     */
    public Map<String, Object> getPageInfo() {
        return pageInfo;
    }

    /**
     * @param pageInfo pagination info map, including hasNextPage (boolean) and endCursor (String)
     */
    public void setPageInfo(Map<String, Object> pageInfo) {
        this.pageInfo = pageInfo;
    }

    /**
     * @return edges List of nodes, where each node is a map from columnName to value
     */
    public List<LinkedHashMap<String, Object>> getEdges() {
        return edges;
    }

    /**
     * @param edges List of nodes, where each node is a map from columnName to value
     */
    public void setEdges(List<LinkedHashMap<String, Object>> edges) {
        this.edges = edges;
    }

    @Override
    public String toString() {
        return "QueryResultSet{" +
                "edges=" + edges +
                ", pageInfo='" + pageInfo.toString() + '\'' +
                '}';
    }
}
