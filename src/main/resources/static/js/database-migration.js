// Database Migration Script
// This script helps migrate localStorage favorites to the database

class DatabaseMigration {
    
    constructor() {
        this.migrationKey = 'database_migration_completed';
    }
    
    /**
     * Check if migration is needed
     */
    isMigrationNeeded() {
        return localStorage.getItem('recipeFavorites') && 
               !localStorage.getItem(this.migrationKey);
    }
    
    /**
     * Migrate localStorage favorites to database
     */
    async migrateFavoritesToDatabase() {
        try {
            const favorites = JSON.parse(localStorage.getItem('recipeFavorites') || '[]');
            
            if (favorites.length === 0) {
                this.markMigrationComplete();
                return { success: true, message: 'No favorites to migrate' };
            }
            
            console.log(`Migrating ${favorites.length} favorites to database...`);
            
            let migratedCount = 0;
            let failedCount = 0;
            
            for (const favorite of favorites) {
                try {
                    // Save recipe to database
                    const response = await fetch('/save-recipe', {
                        method: 'POST',
                        headers: {
                            'Content-Type': 'application/json',
                        },
                        body: JSON.stringify({
                            content: favorite.content,
                            ingredients: favorite.ingredients,
                            cuisine: favorite.cuisine,
                            dietaryRestrictions: favorite.dietaryRestrictions
                        })
                    });
                    
                    if (response.ok) {
                        migratedCount++;
                        console.log(`Migrated: ${favorite.title}`);
                    } else {
                        failedCount++;
                        console.error(`Failed to migrate: ${favorite.title}`);
                    }
                } catch (error) {
                    failedCount++;
                    console.error(`Error migrating ${favorite.title}:`, error);
                }
            }
            
            // Mark migration as complete
            this.markMigrationComplete();
            
            return {
                success: true,
                message: `Migration completed: ${migratedCount} migrated, ${failedCount} failed`
            };
            
        } catch (error) {
            console.error('Migration error:', error);
            return {
                success: false,
                message: 'Migration failed: ' + error.message
            };
        }
    }
    
    /**
     * Mark migration as complete
     */
    markMigrationComplete() {
        localStorage.setItem(this.migrationKey, 'true');
    }
    
    /**
     * Show migration notification
     */
    showMigrationNotification(message) {
        const notification = document.createElement('div');
        notification.className = 'fixed top-4 right-4 bg-blue-500 text-white px-4 py-2 rounded-lg shadow-lg z-50';
        notification.innerHTML = `<i class="fas fa-database mr-2"></i>${message}`;
        document.body.appendChild(notification);
        
        setTimeout(() => {
            notification.remove();
        }, 5000);
    }
    
    /**
     * Initialize migration check
     */
    async init() {
        if (this.isMigrationNeeded()) {
            console.log('Database migration needed...');
            
            // Show migration prompt
            const shouldMigrate = confirm(
                'We found some saved recipes in your browser. Would you like to migrate them to the database for better persistence and features?'
            );
            
            if (shouldMigrate) {
                const result = await this.migrateFavoritesToDatabase();
                this.showMigrationNotification(result.message);
                
                if (result.success) {
                    // Clear localStorage favorites after successful migration
                    localStorage.removeItem('recipeFavorites');
                    console.log('localStorage favorites cleared after migration');
                }
            } else {
                // Mark as complete even if user declines
                this.markMigrationComplete();
            }
        }
    }
}

// Export for use in other scripts
window.DatabaseMigration = DatabaseMigration;
