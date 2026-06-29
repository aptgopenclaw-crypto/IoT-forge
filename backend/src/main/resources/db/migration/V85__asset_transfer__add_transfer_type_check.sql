-- Add check constraint to enforce allowed transfer_type values.
-- Valid values must match AssetTransferCreateRequest @Pattern and frontend TRANSFER_TYPES constant.
ALTER TABLE asset_transfer_applications
    ADD CONSTRAINT chk_asset_transfer_type
        CHECK (transfer_type IN ('INTERNAL', 'EXTERNAL', 'DISPOSAL', 'RETURN'));
