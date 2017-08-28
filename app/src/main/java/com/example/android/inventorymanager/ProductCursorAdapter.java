package com.example.android.inventorymanager;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.android.inventorymanager.data.ProductContract.ProductEntry;

public class ProductCursorAdapter extends CursorAdapter {

    /** Tag for the log messages */
    public static final String LOG_TAG = ProductCursorAdapter.class.getSimpleName();

    /**
     * Constructs a new {@link ProductCursorAdapter}.
     *
     * @param context The context
     * @param c       The cursor from which to get the data.
     */
    public ProductCursorAdapter(Context context, Cursor c) {
        super(context, c, 0 /* flags */);
    }

    /**
     * Makes a new blank list item view. No data is set (or bound) to the views yet.
     *
     * @param context app context
     * @param cursor  The cursor from which to get the data. The cursor is already moved to the correct position.
     * @param parent  The parent to which the new view is attached to
     * @return the newly created list item view.
     */
    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        return LayoutInflater.from(context).inflate(R.layout.list_item, parent, false);
    }

    /**
     * This method binds the product data (in the current row pointed to by cursor) to the given
     * list item layout.
     *
     * @param view    Existing view, returned earlier by newView() method
     * @param context app context
     * @param cursor  The cursor from which to get the data. The cursor is already moved to the correct row.
     */
    @Override
    public void bindView(View view, final Context context, final Cursor cursor) {

        // Get the current position of the cursor in order to set a TAG with it on the sell button
        final int position = cursor.getPosition();

        // Find individual views that we want to modify in the list item layout
        TextView nameTextView = (TextView) view.findViewById(R.id.name);
        TextView unitPriceTextView = (TextView) view.findViewById(R.id.unit_price_value);
        TextView quantityTextView = (TextView) view.findViewById(R.id.stock_level_value);
        ImageView sellNowButtonImageView = (ImageView) view.findViewById(R.id.sell_button);

        // Set a TAG on the sell button with current position of cursor
        sellNowButtonImageView.setTag(position);

        // Find the columns of product attributes that we're interested in
        int nameColumnIndex = cursor.getColumnIndex(ProductEntry.COLUMN_PRODUCT_NAME);
        int unitPriceColumnIndex = cursor.getColumnIndex(ProductEntry.COLUMN_PRODUCT_UNIT_PRICE);
        int quantityColumnIndex = cursor.getColumnIndex(ProductEntry.COLUMN_PRODUCT_QUANTITY);

        // Read the product attributes from the Cursor for the current product
        String productName = cursor.getString(nameColumnIndex);
        int productUnitPrice = cursor.getInt(unitPriceColumnIndex);
        int productQuantity = cursor.getInt(quantityColumnIndex);

        // Update the TextViews with the attributes for the current product
        nameTextView.setText(productName);
        unitPriceTextView.setText(context.getString(R.string.currency_product_unit_price) + String.format("%,d", productUnitPrice));
        quantityTextView.setText(String.valueOf(productQuantity));

        // Update the color of the displayed product's quantity according to its stock level
        if (productQuantity == 0) {
            quantityTextView.setTextColor(ContextCompat.getColor(context, R.color.colorEmptyStock));
        }
        else {
            quantityTextView.setTextColor(ContextCompat.getColor(context, R.color.colorPositiveStock));
        }

        sellNowButtonImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Get the TAG of the view (sell ImageView) that was clicked on to arrive here
                // Define a position from this TAG
                Integer position = (Integer) v.getTag();
                // Move the cursor to this position
                cursor.moveToPosition(position);
                // Get the rowID  in the database of the product for which a sell is requested
                Long rowId = cursor.getLong(cursor.getColumnIndex(ProductEntry._ID));
                // Find the column of product attributes that we're interested in : Quantity.
                int quantityColumnIndex = cursor.getColumnIndex(ProductEntry.COLUMN_PRODUCT_QUANTITY);
                // Get the current stock level (quantity) of the product for which a sell is requested
                int currentQuantity = cursor.getInt(quantityColumnIndex);

                // If the stock level is still positive, then proceed with the sell of 1 unit
                if(currentQuantity > 0) {
                    sellProductUnit(context, rowId, currentQuantity);
                }
                else {
                    // Otherwise, show a toast message saying that the sell action is not possible
                    // as the stock level has reached 0.
                    Toast.makeText(context, context.getString(R.string.catalog_sell_product_item_failed_stock_empty),
                            Toast.LENGTH_SHORT).show();
                }

            }
        });
    }

    /**
     * Helper method to sell a unit of a product
     */
    private void sellProductUnit(Context context, Long rowId, int quantity) {
        // sell 1 unit of the product by decrement its stock level by 1 unit.
        quantity--;
        // Form the content URI that represents the specific product that was clicked on.
        Uri currentProductUri = ContentUris.withAppendedId(ProductEntry.CONTENT_URI, rowId);
        // Create a ContentValues objectURI and put the decremented stock level in it
        final ContentValues values = new ContentValues();
        values.put(ProductEntry.COLUMN_PRODUCT_QUANTITY, quantity);

        // execute the modification in the database,
        // and get the integer for the rows affected by the update.
        int rowsAffected = context.getContentResolver().update(currentProductUri, values, null, null);
        // Show a toast message depending on whether or not the update was successful.
        if (rowsAffected == 0) {
            // If no rows were affected, then there was an error with the update.
            Toast.makeText(context, context.getString(R.string.catalog_sell_product_item_failed),
                    Toast.LENGTH_SHORT).show();

        } else {
            // Otherwise, the sell was successful and we can display a toast.
            Toast.makeText(context, context.getString(R.string.catalog_sell_product_item_successful),
                    Toast.LENGTH_SHORT).show();
        }
    }
}