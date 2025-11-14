package com.example

import kotlinx.coroutines.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.Transaction
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.StringColumnType
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

// --- CORRECTED USERS TABLE ---
object Users : IdTable<String>("users") {
    // FIX 1: Added .uniqueIndex() to explicitly enforce uniqueness for the foreign key reference
    override val id: Column<EntityID<String>> = varchar("id", 128).entityId().uniqueIndex()
    
    val email = varchar("email", 255).uniqueIndex()
    val passwordHash = varchar("passwordhash", 255)
    val personalName = varchar("personal_name", 1024).nullable()
    val breederName = varchar("breeder_name", 1024).nullable()
    val profilePictureUrl = varchar("profile_picture_url", 2048).nullable()
    val isBreederProfile = bool("is_breeder_profile").default(false)
    val sequentialId = integer("sequential_id").autoIncrement()
}

// --- FULL ANIMALS TABLE DEFINITION (FIXED) ---
object Animals : IdTable<String>("animals") {
    // Defines the primary key as a String (UUID)
    override val id: Column<EntityID<String>> = varchar("id", 128).entityId()
    
    val sequentialId = integer("sequential_id").autoIncrement()

    // Foreign Key to Users table
    // Correctly defines the foreign key relationship to the String ID of the Users table
    val userId = reference("user_id", Users.id, onDelete = ReferenceOption.CASCADE)
    
    // Animal Details
    val name = varchar("name", 1024).nullable()
    val species = varchar("species", 1024)
    val breeder = varchar("breeder", 1024).nullable() // CORRECTED FROM 'breed'
    val birthDate = date("birth_date").nullable()
    val gender = varchar("gender", 16).nullable()
    val colorVariety = varchar("color_variety", 1024).nullable()
    val coatVariety = varchar("coat_variety", 1024).nullable()
    
    val registryCode = varchar("registry_code", 1024).nullable()
    val owner = varchar("owner", 1024).nullable()
    val remarks = varchar("remarks", 2048).nullable()
    // NOTE: fatherId/motherId are kept as plain strings (not foreign keys) based on the original schema design
    val fatherId = varchar("father_id", 128).nullable()
    val motherId = varchar("mother_id", 128).nullable()
    
    val showOnProfile = bool("show_on_profile").default(false)
    val showRegistryCode = bool("show_registry_code").default(false)
    val showOwner = bool("show_owner").default(false)
    val showRemarks = bool("show_remarks").default(false)
    val showParents = bool("show_parents").default(false)
    val geneticsCode = varchar("genetics_code", 1024).nullable()
}

fun initDatabase() {
    val dbDriverClassName = "org.postgresql.Driver"
    val dbJdbcURL = System.getenv("JDBC_URL") 
        ?: throw IllegalStateException("JDBC_URL environment variable not set.")
    val dbUser = System.getenv("DB_USER") 
        ?: "postgres" // Fallback for local testing, but required for Render
    val dbPassword = System.getenv("DB_PASSWORD") 
        ?: "mysecretpassword" // Fallback for local testing, but required for Render

    val config = HikariConfig().apply {
        driverClassName = dbDriverClassName
        jdbcUrl = dbJdbcURL
        username = dbUser
        password = dbPassword
        maximumPoolSize = 3
        isAutoCommit = false
        transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        validate()
    }
    val dataSource = HikariDataSource(config)
    Database.connect(dataSource)

    // Use a standard transaction for schema creation
    transaction {
        SchemaUtils.create(Users, Animals)
    }
}

// Helper to bridge Ktor and Exposed
suspend fun <T> dbQuery(block: suspend Transaction.() -> T): T =
    newSuspendedTransaction(Dispatchers.IO) { block() }
