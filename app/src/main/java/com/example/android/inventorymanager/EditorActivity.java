package com.example.android.inventorymanager;

import android.Manifest;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NavUtils;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.android.inventorymanager.data.ProductContract.ProductEntry;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * Allows the user to create a new product or edit an existing one.
 */
public class EditorActivity extends AppCompatActivity implements LoaderManager.LoaderCallbacks<Cursor> {

    /** Tag for the log messages */
    public static final String LOG_TAG = EditorActivity.class.getSimpleName();

    /** Constants definition */
    private static final int EXISTING_PRODUCT_LOADER = 0;
    private static final int MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 0;
    private static final String EMAIL_PATTERN = "[a-zA-Z0-9._-]+@[a-z]+\\.+[a-z]+";
    private static int PICK_IMAGE_REQUEST = 1;

    /** Global variables declaration */
    private Uri selectedImageUri = null;
    private Uri mCurrentProductUri;

    private String productName;
    private String supplierName;
    private String supplierEmail;
    private int productQuantity;

    private ImageView mPictureImageView;
    private EditText mNameEditText;
    private EditText mSupplierNameEditText;
    private EditText mSupplierEmailAddressEditText;
    private EditText mUnitPriceEditText;
    private TextView mProductQuantityTextView;

    private boolean mProductHasChanged = false;
    private boolean imageProductHasChanged = false;
    private boolean productDataAreValid = true;

    /**
     * OnTouchListener that listens for any touch on a View.
     */
    private View.OnTouchListener mTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            mProductHasChanged = true;
            return false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editor);

        mPictureImageView = (ImageView) findViewById(R.id.image_product);
        mNameEditText = (EditText) findViewById(R.id.edit_product_name);
        mSupplierNameEditText = (EditText) findViewById(R.id.edit_product_supplier_name);
        mSupplierEmailAddressEditText = (EditText) findViewById(R.id.edit_product_supplier_email);
        mUnitPriceEditText = (EditText) findViewById(R.id.edit_product_unit_price);
        mProductQuantityTextView = (TextView) findViewById(R.id.product_quantity_text_view);

        ImageView mOrderNowImageView = (ImageView) findViewById(R.id.order_now);
        ImageView mDecrementStock = (ImageView) findViewById(R.id.decrement_stock);
        ImageView mIncrementStock = (ImageView) findViewById(R.id.increment_stock);

        // Examine the intent that was used to launch this activity.
        Intent intent = getIntent();
        mCurrentProductUri = intent.getData();

        if (mCurrentProductUri == null) {
            setTitle(getString(R.string.editor_activity_title_new_product));
            invalidateOptionsMenu();
            productQuantity = 0;
            mProductQuantityTextView.setText(String.valueOf(productQuantity));
            mOrderNowImageView.setVisibility(View.GONE);
        } else {
            setTitle(getString(R.string.editor_activity_title_edit_product));
            getSupportLoaderManager().initLoader(EXISTING_PRODUCT_LOADER, null, this);
        }

        // Setup OnTouchListeners on all the input fields,
        // to notify the user if he tries to leave the editor without saving.
        mPictureImageView.setOnTouchListener(mTouchListener);
        mNameEditText.setOnTouchListener(mTouchListener);
        mSupplierNameEditText.setOnTouchListener(mTouchListener);
        mSupplierEmailAddressEditText.setOnTouchListener(mTouchListener);
        mUnitPriceEditText.setOnTouchListener(mTouchListener);
        mDecrementStock.setOnTouchListener(mTouchListener);
        mIncrementStock.setOnTouchListener(mTouchListener);

        mPictureImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("image/*");
                startActivityForResult(intent, PICK_IMAGE_REQUEST);
            }
        });

        mDecrementStock.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (productQuantity > 0) {
                    productQuantity--;
                    displayProductStockLevel(productQuantity);
                }
            }
        });

        mIncrementStock.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                productQuantity++;
                displayProductStockLevel(productQuantity);
            }
        });

        mOrderNowImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_SENDTO);
                intent.setData(Uri.parse("mailto:")); // only email apps should handle this
                intent.putExtra(Intent.EXTRA_EMAIL, new String[] {supplierEmail});
                intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.email_subject_prefix) + productName);
                intent.putExtra(Intent.EXTRA_TEXT, createOrderEmailMessage());
                if (intent.resolveActivity(getPackageManager()) != null) {
                    startActivity(intent);
                }
            }
        });
    }

    /**
     * This method creates a summary for the order email.
     */
    public String createOrderEmailMessage() {

        String orderEmailMessage = getString(R.string.order_email_starting_greeting) + supplierName;
        orderEmailMessage += getString(R.string.order_email_comma);
        orderEmailMessage += getString(R.string.order_email_jump_line);
        orderEmailMessage += getString(R.string.order_email_current_stock_statement_part_one) + productName;
        orderEmailMessage += getString(R.string.order_email_current_stock_statement_part_two) + productQuantity;
        orderEmailMessage += getString(R.string.order_email_point);
        orderEmailMessage += getString(R.string.order_email_jump_line);
        orderEmailMessage += getString(R.string.order_email_request_end_greetings);
        return orderEmailMessage;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // Check which request we are responding to.
        if (requestCode == PICK_IMAGE_REQUEST) {
            // Make sure the request was successful.
            if (resultCode == RESULT_OK) {
                try {
                    selectedImageUri = data.getData();
                    imageProductHasChanged = true;
                    final InputStream imageStream = getContentResolver().openInputStream(selectedImageUri);
                    Bitmap selectedImage = BitmapFactory.decodeStream(imageStream);
                    mPictureImageView.setImageBitmap(selectedImage);
                } catch (IOException e) {
                    e.printStackTrace();
                    Toast.makeText(EditorActivity.this, "Something went wrong", Toast.LENGTH_LONG).show();
                }
                // The request was not successful.
            } else {
                Toast.makeText(EditorActivity.this, "You haven't picked Image", Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * This method displays the given quantity value on the screen.
     */
    private void displayProductStockLevel(int currentStock) {
        TextView quantityTextView = (TextView) findViewById(R.id.product_quantity_text_view);
        quantityTextView.setText(String.valueOf(currentStock));
    }

    /**
     * Get user input from editor and save new product into the database or update an existing one.
     */
    private void saveProduct() {

        String nameString = mNameEditText.getText().toString().trim();
        String supplierNameString = mSupplierNameEditText.getText().toString().trim();
        String supplierEmailString = mSupplierEmailAddressEditText.getText().toString().trim();
        String unitPriceString = mUnitPriceEditText.getText().toString().trim();

        // Check if this is supposed to be a new product,
        // and check if all the fields in the editor are blank.
        if (mCurrentProductUri == null && !imageProductHasChanged && TextUtils.isEmpty(nameString) && TextUtils.isEmpty(supplierNameString) &&
                TextUtils.isEmpty(supplierEmailString) && TextUtils.isEmpty(unitPriceString) && productQuantity ==0) {
            return;
        }

        // Create a ContentValues object where column names are the keys,
        // and product attributes from the editor are the values.
        ContentValues values = new ContentValues();

        // Check that the product has a name.
        if (!nameString.isEmpty()) {
            values.put(ProductEntry.COLUMN_PRODUCT_NAME, nameString);
        }
        else {
            Toast.makeText(this, getString(R.string.editor_product_requires_name),
                    Toast.LENGTH_SHORT).show();
            productDataAreValid = false;
            return;
        }

        // Check that the name of the supplier of the product is provided.
        if (!supplierNameString.isEmpty()) {
            values.put(ProductEntry.COLUMN_PRODUCT_SUPPLIER_NAME, supplierNameString);
        }
        else {
            Toast.makeText(this, getString(R.string.editor_product_requires_supplier_name),
                    Toast.LENGTH_SHORT).show();
            productDataAreValid = false;
            return;
        }

        // Check that the email of the supplier of the product has been provided.
        if (!supplierNameString.isEmpty() && supplierEmailString.matches(EMAIL_PATTERN)) {
            values.put(ProductEntry.COLUMN_PRODUCT_SUPPLIER_EMAIL, supplierEmailString);
        }
        else if (supplierEmailString.isEmpty()) {
            Toast.makeText(this, getString(R.string.editor_product_requires_supplier_email),
                    Toast.LENGTH_SHORT).show();
            productDataAreValid = false;
            return;
        } else if (!supplierEmailString.matches(EMAIL_PATTERN)) {
            Toast.makeText(this, getString(R.string.editor_product_invalid_supplier_email),
                    Toast.LENGTH_SHORT).show();
            productDataAreValid = false;
            return;
        }

        // Check that the product has a valid unit price.
        if (!unitPriceString.isEmpty() && Integer.parseInt(unitPriceString) > 0) {
            values.put(ProductEntry.COLUMN_PRODUCT_UNIT_PRICE, unitPriceString);
        }
        else if (unitPriceString.length() == 0){
            Toast.makeText(this, getString(R.string.editor_product_requires_price),
                    Toast.LENGTH_SHORT).show();
            productDataAreValid = false;
            return;
        } else if (Integer.parseInt(unitPriceString) == 0) {
            Toast.makeText(this, getString(R.string.editor_product_requires_positive_price),
                    Toast.LENGTH_SHORT).show();
            productDataAreValid = false;
            return;
        }

        // Check that the product has a valid quantity.
        if (productQuantity > 0) {
            values.put(ProductEntry.COLUMN_PRODUCT_QUANTITY, productQuantity);
        }
        else {
            Toast.makeText(this, getString(R.string.editor_product_requires_positive_stock_level),
                    Toast.LENGTH_SHORT).show();
            productDataAreValid = false;
            return;
        }

        // Check that the product has an image
        if (imageProductHasChanged) {
            String imageString = selectedImageUri.toString();
            values.put(ProductEntry.COLUMN_PRODUCT_IMAGE_PATH, imageString);
        }
        else if (((BitmapDrawable)mPictureImageView.getDrawable()).getBitmap() == ((BitmapDrawable)getDrawable(R.drawable.img_generic)).getBitmap()){
            Toast.makeText(this, getString(R.string.editor_product_requires_image),
                    Toast.LENGTH_SHORT).show();
            productDataAreValid = false;
            return;
        }

        // If all the checks on the input values are passed,
        // then the data for the product are valid.
        productDataAreValid = true;

        if (mCurrentProductUri == null) {
            Uri newUri = getContentResolver().insert(ProductEntry.CONTENT_URI, values);
            if (newUri == null) {
                Toast.makeText(this, getString(R.string.editor_insert_product_failed),
                        Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, getString(R.string.editor_insert_product_successful),
                        Toast.LENGTH_SHORT).show();
            }
        } else {
            int rowsAffected = getContentResolver().update(mCurrentProductUri, values, null, null);
            if (rowsAffected == 0) {
                Toast.makeText(this, getString(R.string.editor_update_product_failed),
                        Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, getString(R.string.editor_update_product_successful),
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_editor, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        if (mCurrentProductUri == null) {
            MenuItem menuItem = menu.findItem(R.id.action_delete);
            menuItem.setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_save:
                saveProduct();
                if (productDataAreValid) {
                    finish();
                }
                return true;
            case R.id.action_delete:
                showDeleteConfirmationDialog();
                return true;
            case android.R.id.home:
                if (!mProductHasChanged) {
                    NavUtils.navigateUpFromSameTask(EditorActivity.this);
                    return true;
                }
                DialogInterface.OnClickListener discardButtonClickListener =
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                NavUtils.navigateUpFromSameTask(EditorActivity.this);
                            }
                        };
                showUnsavedChangesDialog(discardButtonClickListener);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * This method is called when the back button is pressed.
     */
    @Override
    public void onBackPressed() {
        if (!mProductHasChanged) {
            super.onBackPressed();
            return;
        }
        DialogInterface.OnClickListener discardButtonClickListener =
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        finish();
                    }
                };
        showUnsavedChangesDialog(discardButtonClickListener);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {

        String[] projection = {
                ProductEntry._ID,
                ProductEntry.COLUMN_PRODUCT_IMAGE_PATH,
                ProductEntry.COLUMN_PRODUCT_NAME,
                ProductEntry.COLUMN_PRODUCT_SUPPLIER_NAME,
                ProductEntry.COLUMN_PRODUCT_SUPPLIER_EMAIL,
                ProductEntry.COLUMN_PRODUCT_UNIT_PRICE,
                ProductEntry.COLUMN_PRODUCT_QUANTITY};

        return new CursorLoader(this,   // Parent activity context
                mCurrentProductUri,     // Query the content URI for the current product
                projection,             // Columns to include in the resulting Cursor
                null,                   // No selection clause
                null,                   // No selection arguments
                null);                  // Default sort order
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {

        if (cursor.moveToFirst()) {
            int imageColumnIndex = cursor.getColumnIndex(ProductEntry.COLUMN_PRODUCT_IMAGE_PATH);
            int nameColumnIndex = cursor.getColumnIndex(ProductEntry.COLUMN_PRODUCT_NAME);
            int supplierNameColumnIndex = cursor.getColumnIndex(ProductEntry.COLUMN_PRODUCT_SUPPLIER_NAME);
            int supplierEmailColumnIndex = cursor.getColumnIndex(ProductEntry.COLUMN_PRODUCT_SUPPLIER_EMAIL);
            int unitPriceColumnIndex = cursor.getColumnIndex(ProductEntry.COLUMN_PRODUCT_UNIT_PRICE);
            int quantityColumnIndex = cursor.getColumnIndex(ProductEntry.COLUMN_PRODUCT_QUANTITY);

            String productImageURI = cursor.getString(imageColumnIndex);
            productName = cursor.getString(nameColumnIndex);
            supplierName = cursor.getString(supplierNameColumnIndex);
            supplierEmail = cursor.getString(supplierEmailColumnIndex);
            int productUnitPrice = cursor.getInt(unitPriceColumnIndex);
            productQuantity = cursor.getInt(quantityColumnIndex);

            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {

                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                            MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE);
                }

            if (productImageURI != null) {
                try {
                    final InputStream imageStream = getContentResolver().openInputStream(Uri.parse(productImageURI));
                    Bitmap productImage = BitmapFactory.decodeStream(imageStream);
                    mPictureImageView.setImageBitmap(productImage);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
            } else {
                mPictureImageView.setImageDrawable(getDrawable(R.drawable.img_generic));
            }

            mNameEditText.setText(productName);
            mSupplierNameEditText.setText(supplierName);
            mSupplierEmailAddressEditText.setText(supplierEmail);
            mUnitPriceEditText.setText(Integer.toString(productUnitPrice));
            mProductQuantityTextView.setText(Integer.toString(productQuantity));

            // Update the color of the displayed product's quantity according to its stock level
            if (productQuantity == 0) {
                mProductQuantityTextView.setTextColor(ContextCompat.getColor(getBaseContext(), R.color.colorEmptyStock));
            }
            else {
                mProductQuantityTextView.setTextColor(ContextCompat.getColor(getBaseContext(), R.color.colorPositiveStock));
            }
            cursor.close();
        }
        cursor.close();
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        // If the loader is invalidated, clear out all the data from the input fields.
        mPictureImageView.setImageBitmap(null);
        mNameEditText.setText("");
        mSupplierNameEditText.setText("");
        mSupplierEmailAddressEditText.setText("");
        mUnitPriceEditText.setText("");
        mProductQuantityTextView.setText("");
    }

    /**
     * Show a dialog that warns the user there are unsaved changes that will be lost
     * if they continue leaving the editor.
     *
     * @param discardButtonClickListener is the click listener for what to do when
     *                                   the user confirms they want to discard their changes
     */
    private void showUnsavedChangesDialog(
            DialogInterface.OnClickListener discardButtonClickListener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.unsaved_changes_dialog_msg);
        builder.setPositiveButton(R.string.discard, discardButtonClickListener);
        builder.setNegativeButton(R.string.keep_editing, null);
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    private void showDeleteConfirmationDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.delete_dialog_msg);
        builder.setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                deleteProduct();
            }
        });
        builder.setNegativeButton(R.string.cancel, null);
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    /**
     * Perform the deletion of the product in the database.
     */
    private void deleteProduct() {
        if (mCurrentProductUri != null) {
            int rowsDeleted = getContentResolver().delete(mCurrentProductUri, null, null);
            if (rowsDeleted == 0) {
                Toast.makeText(this, getString(R.string.editor_delete_product_failed),
                        Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, getString(R.string.editor_delete_product_successful),
                        Toast.LENGTH_SHORT).show();
            }
        }
        finish();
    }
}
