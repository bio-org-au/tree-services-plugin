package au.org.biodiversity.nsl.tree

import org.hibernate.Session
import org.hibernate.SessionFactory
import org.hibernate.jdbc.Work

import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException

final class HibernateSessionUtils {

    static class WorkResultHolder implements Work {
        Object result = null
        Closure exec

        WorkResultHolder(Closure exec) {
            this.exec = exec
        }

        public void execute(Connection connection) throws SQLException {
            result = exec(connection)
        }
    }

    static Object doWork(SessionFactory sessionFactory_nsl, final Closure c) throws SQLException {
        Session s = sessionFactory_nsl.getCurrentSession()
        WorkResultHolder w = new WorkResultHolder(c)
        s.doWork(w)
        return w.result
    }

    static void create_tree_temp_id(Connection cnct) {
        withQ cnct,
                '''create temporary table if not exists tree_temp_id (
					id bigint primary key,
					id2 bigint
				)
				on commit delete rows''', { PreparedStatement qry -> qry.executeUpdate() }

        withQ cnct, '''delete from tree_temp_id''', { PreparedStatement qry -> qry.executeUpdate() }
    }

    static void create_tree_temp_id2(Connection cnct) {
        withQ cnct,
                '''create temporary table if not exists tree_temp_id2 (
					id bigint primary key,
					id2 bigint
				)
				on commit delete rows''', { PreparedStatement qry -> qry.executeUpdate() }

        withQ cnct, '''delete from tree_temp_id2''', { PreparedStatement qry -> qry.executeUpdate() }
    }

    static void create_tree_temp_id3(Connection cnct) {
        withQ cnct,
                '''create temporary table if not exists tree_temp_id3 (
					id bigint primary key,
					id2 bigint
				)
				on commit delete rows''', { PreparedStatement qry -> qry.executeUpdate() }

        withQ cnct, '''delete from tree_temp_id3''', { PreparedStatement qry -> qry.executeUpdate() }
    }

    static void create_tree_replacements(Connection cnct) {
        withQ cnct,
                '''create temporary table if not exists tree_replacements (
			id bigint primary key,
			id2 bigint
		)
		on commit delete rows''', { PreparedStatement qry -> qry.executeUpdate() }

        withQ cnct, '''delete from tree_replacements''', { PreparedStatement qry -> qry.executeUpdate() }
    }

    static void create_link_treewalk(Connection cnct) {
        withQ cnct,
                '''create temporary table if not exists link_treewalk (
			id bigint primary key,
			supernode_id bigint,
			subnode_id bigint
		)
		on commit delete rows''', { PreparedStatement qry -> qry.executeUpdate() }

        withQ cnct, '''delete from link_treewalk''', { PreparedStatement qry -> qry.executeUpdate() }
    }

    static void create_tree_syn_replacements(Connection cnct) {
        withQ cnct,
                '''create temporary table if not exists tree_syn_replacements (
			id bigint primary key,
			id2 bigint,
			pass_indicator char(1)
		)
		on commit delete rows''', { PreparedStatement qry -> qry.executeUpdate() }

        withQ cnct, '''delete from tree_syn_replacements''', { PreparedStatement qry -> qry.executeUpdate() }
    }

    static Object withQ(Connection cnct, sql, Closure c) throws SQLException {
        SimpleProfiler.start(sql)
        PreparedStatement stmt = cnct.prepareStatement(sql.toString())
        try {
            return c(stmt)
        }
        finally {
            stmt.close()
            SimpleProfiler.end(sql)
        }
    }

    static Object withQresult(Connection cnct, sql, Closure c = null) throws SQLException {
        SimpleProfiler.start(sql)

        PreparedStatement stmt = cnct.prepareStatement(sql.toString())
        try {
            if (c != null) {
                c(stmt)
            }
            ResultSet rs = stmt.executeQuery()
            try {
                Object r
                if (!rs.next()) {
                    return null
                } else {
                    Object o = rs.getObject(1)
                    if (rs.next()) {
                        throw new IllegalStateException("Single-row query returns multiple results")
                    }
                    return o
                }
            }
            finally {
                rs.close()
            }
        }
        finally {
            stmt.close()
            SimpleProfiler.end(sql)
        }
    }
}

