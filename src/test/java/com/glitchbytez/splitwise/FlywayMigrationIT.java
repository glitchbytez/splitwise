package com.glitchbytez.splitwise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import com.glitchbytez.splitwise.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** Verifies that the Flyway migrations apply cleanly and that the schema enforces its invariants. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
class FlywayMigrationIT {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void allMigrationsAppliedSuccessfully() {
        List<Boolean> results = jdbc.queryForList("select success from flyway_schema_history", Boolean.class);

        assertThat(results).isNotEmpty().containsOnly(true);
    }

    @Test
    void createsEveryTableFromTheSpec() {
        List<String> tables = jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = 'public'", String.class);

        assertThat(tables).contains(
                "users", "groups", "group_members", "expenses", "expense_shares", "settlements", "activity_log");
    }

    @Test
    void createsTheRequiredIndexes() {
        List<String> indexes = jdbc.queryForList(
                "select indexname from pg_indexes where schemaname = 'public'", String.class);

        assertThat(indexes).contains(
                "idx_expenses_group_date",
                "uk_expense_shares_expense_user",
                "idx_settlements_group",
                "idx_activity_log_group_created",
                "idx_group_members_user");
    }

    @Test
    void storesMoneyAsBigintMinorUnits() {
        List<String> types = jdbc.queryForList("""
                select data_type from information_schema.columns
                where table_schema = 'public' and column_name in ('amount_minor', 'share_minor')
                """, String.class);

        assertThat(types).hasSize(3).containsOnly("bigint");
    }

    @Test
    void rejectsDuplicateEmails() {
        insertUser("alice@example.com");

        assertThatThrownBy(() -> insertUser("alice@example.com"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsEmailsThatAreNotLowerCase() {
        assertThatThrownBy(() -> insertUser("Alice@Example.com"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_users_email_lower_case");
    }

    // One violation per test: PostgreSQL aborts the surrounding test transaction after the first error.
    @ParameterizedTest
    @CsvSource({
            "1000, EUR, EQUAL, ck_expenses_currency_usd",
            "0, USD, EQUAL, ck_expenses_amount_positive",
            "-1, USD, EQUAL, ck_expenses_amount_positive",
            "1000, USD, SHARES, ck_expenses_split_type"
    })
    void rejectsInvalidExpenses(long amountMinor, String currency, String splitType, String violatedConstraint) {
        UUID user = insertUser("payer@example.com");
        UUID group = insertGroup(user);

        assertThatThrownBy(() -> insertExpense(group, user, amountMinor, currency, splitType))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(violatedConstraint);
    }

    @Test
    void acceptsAValidExpenseWithDefaults() {
        UUID user = insertUser("payer@example.com");
        UUID group = insertGroup(user);

        UUID expense = insertExpense(group, user, 9000, "USD", "PERCENTAGE");

        assertThat(jdbc.queryForObject("select version from expenses where id = ?", Long.class, expense)).isZero();
    }

    @Test
    void rejectsSettlementsToOneself() {
        UUID user = insertUser("self@example.com");
        UUID group = insertGroup(user);

        assertThatThrownBy(() -> jdbc.update("""
                insert into settlements (group_id, from_user, to_user, amount_minor, created_by)
                values (?, ?, ?, 100, ?)
                """, group, user, user, user))
                .hasMessageContaining("ck_settlements_not_self");
    }

    @Test
    void rejectsDuplicateMembership() {
        UUID user = insertUser("member@example.com");
        UUID group = insertGroup(user);
        jdbc.update("insert into group_members (group_id, user_id, role) values (?, ?, 'OWNER')", group, user);

        assertThatThrownBy(() -> jdbc.update(
                "insert into group_members (group_id, user_id, role) values (?, ?, 'MEMBER')", group, user))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsUnknownMemberRoles() {
        UUID user = insertUser("member@example.com");
        UUID group = insertGroup(user);

        assertThatThrownBy(() -> jdbc.update(
                "insert into group_members (group_id, user_id, role) values (?, ?, 'ADMIN')", group, user))
                .hasMessageContaining("ck_group_members_role");
    }

    private UUID insertUser(String email) {
        return jdbc.queryForObject(
                "insert into users (email, display_name, password_hash) values (?, 'Test', 'x') returning id",
                UUID.class, email);
    }

    private UUID insertGroup(UUID createdBy) {
        return jdbc.queryForObject(
                "insert into groups (name, created_by) values ('Trip', ?) returning id", UUID.class, createdBy);
    }

    private UUID insertExpense(UUID group, UUID payer, long amountMinor, String currency, String splitType) {
        return jdbc.queryForObject("""
                insert into expenses (group_id, description, amount_minor, currency, expense_date,
                                      paid_by, split_type, created_by)
                values (?, 'Dinner', ?, ?, current_date, ?, ?, ?) returning id
                """, UUID.class, group, amountMinor, currency, payer, splitType, payer);
    }
}
