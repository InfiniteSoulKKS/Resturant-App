import React, { useState } from 'react';
import { MenuItem } from '../types';
import { addMenuItemDB, updateMenuItemDB, deleteMenuItemDB } from '../lib/firebase';
import {
  Plus,
  Edit3,
  Trash2,
  X,
  Sparkles,
  IndianRupee,
  Image as ImageIcon,
  Utensils,
  Tag,
  Flame,
  CheckCircle2,
} from 'lucide-react';

interface MenuManagementProps {
  menuItems: MenuItem[];
}

export const MenuManagement: React.FC<MenuManagementProps> = ({ menuItems }) => {
  const [selectedCategory, setSelectedCategory] = useState<string>('All Items');
  const [editingId, setEditingId] = useState<string | null>(null);

  // Form State
  const [itemName, setItemName] = useState('');
  const [price, setPrice] = useState('');
  const [category, setCategory] = useState<'Appetizers' | 'Mains' | 'Desserts' | 'Beverages' | 'Breads' | 'Starters'>('Mains');
  const [description, setDescription] = useState('');
  const [status, setStatus] = useState<'Available' | 'Sold Out'>('Available');
  const [imageUrl, setImageUrl] = useState('');
  const [isVeg, setIsVeg] = useState<boolean>(true);
  const [spiceLevel, setSpiceLevel] = useState<string>('Medium');
  const [isMobileFormOpen, setIsMobileFormOpen] = useState(false);

  const categories = ['All Items', 'Starters', 'Mains', 'Breads', 'Desserts', 'Beverages'];

  const filteredItems = menuItems.filter((item) => {
    if (selectedCategory === 'All Items') return true;
    return item.category === selectedCategory;
  });

  const handleOpenEdit = (item: MenuItem) => {
    setEditingId(item.id);
    setItemName(item.title);
    setPrice(item.price.toString());
    setCategory(item.category as any);
    setDescription(item.description);
    setStatus(item.status);
    setImageUrl(item.imageUrl);
    setIsVeg(item.isVeg !== false);
    setSpiceLevel(item.spiceLevel || 'Medium');
    setIsMobileFormOpen(true);
  };

  const handleResetForm = () => {
    setEditingId(null);
    setItemName('');
    setPrice('');
    setCategory('Mains');
    setDescription('');
    setStatus('Available');
    setImageUrl('');
    setIsVeg(true);
    setSpiceLevel('Medium');
    setIsMobileFormOpen(false);
  };

  const handleSaveItem = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!itemName.trim() || !price) return;

    const numPrice = parseFloat(price) || 0;
    const finalImageUrl =
      imageUrl.trim() ||
      'https://images.unsplash.com/photo-1546069901-ba9599a7e63c?auto=format&fit=crop&q=80&w=800';

    if (editingId) {
      await updateMenuItemDB(editingId, {
        title: itemName,
        price: numPrice,
        category,
        description,
        status,
        imageUrl: finalImageUrl,
        tag: category,
        isVeg,
        spiceLevel,
      });
    } else {
      await addMenuItemDB({
        title: itemName,
        price: numPrice,
        category,
        description,
        status,
        imageUrl: finalImageUrl,
        tag: category,
        isVeg,
        spiceLevel,
      });
    }

    handleResetForm();
  };

  const handleDelete = async (id: string) => {
    if (confirm('Are you sure you want to delete this delicacy from the menu?')) {
      await deleteMenuItemDB(id);
    }
  };

  return (
    <div className="pt-20 px-4 md:px-8 mb-24 md:mb-12 max-w-[1440px] mx-auto">
      {/* Page Header */}
      <div className="flex justify-between items-end pb-4 border-b border-stone-800">
        <div>
          <h2 className="text-2xl md:text-3xl font-bold font-serif text-stone-100 tracking-tight flex items-center gap-2">
            <Sparkles className="w-7 h-7 text-amber-400" />
            <span>Culinary Menu Management</span>
          </h2>
          <p className="text-xs text-stone-400 mt-1">
            Real-time catalog control: modify dishes, set live stock status, and adjust pricing instantly across the app.
          </p>
        </div>

        {/* Desktop Add Button */}
        <button
          onClick={() => {
            handleResetForm();
            setIsMobileFormOpen(true);
          }}
          className="hidden md:flex items-center gap-2 bg-amber-500 hover:bg-amber-400 text-stone-950 text-xs font-bold px-4 py-2.5 rounded-xl transition-all shadow-lg shadow-amber-500/20 cursor-pointer"
        >
          <Plus className="w-4 h-4 stroke-[3]" />
          <span>Add New Delicacy</span>
        </button>
      </div>

      {/* Categories Filter */}
      <div className="flex gap-2 overflow-x-auto pb-2 hide-scrollbar my-4">
        {categories.map((cat) => {
          const isActive = selectedCategory === cat;
          return (
            <button
              key={cat}
              onClick={() => setSelectedCategory(cat)}
              className={`px-4 py-2 rounded-xl text-xs font-medium whitespace-nowrap transition-all cursor-pointer ${
                isActive
                  ? 'bg-amber-500 text-stone-950 font-bold shadow-md shadow-amber-500/20'
                  : 'bg-stone-900 text-stone-400 hover:text-stone-100 hover:bg-stone-800/60 border border-stone-800'
              }`}
            >
              {cat}
            </button>
          );
        })}
      </div>

      {/* Content Split Layout */}
      <div className="flex flex-col xl:flex-row gap-6 h-full pb-12">
        {/* Menu List (Left Side) */}
        <div className="flex-1 grid grid-cols-1 md:grid-cols-2 gap-4 content-start">
          {filteredItems.map((item) => {
            const isOut = item.status === 'Sold Out';
            return (
              <div
                key={item.id}
                className={`bg-stone-900/80 backdrop-blur-md rounded-2xl p-4 flex flex-col gap-3 border border-stone-800 shadow-xl hover:border-stone-700/80 transition-all ${
                  isOut ? 'opacity-70' : ''
                }`}
              >
                {/* Image Box */}
                <div
                  className={`h-40 w-full rounded-xl overflow-hidden bg-stone-950 relative ${
                    isOut ? 'grayscale' : ''
                  }`}
                >
                  <img
                    src={item.imageUrl}
                    alt={item.title}
                    className="w-full h-full object-cover"
                    referrerPolicy="no-referrer"
                  />
                  {isOut && (
                    <div className="absolute top-2 left-2 bg-rose-950/90 text-rose-400 border border-rose-800/80 px-2 py-0.5 rounded-lg text-[10px] font-bold uppercase shadow">
                      Sold Out
                    </div>
                  )}
                  <div className="absolute top-2 right-2 bg-stone-950/80 backdrop-blur-md px-2 py-0.5 rounded-lg text-[10px] text-amber-400 font-bold uppercase border border-stone-700/50 shadow">
                    {item.category}
                  </div>
                </div>

                {/* Info */}
                <div className="flex justify-between items-start pt-1">
                  <div className="flex-1">
                    <h3 className="text-sm font-bold font-serif text-stone-100 flex items-center gap-1.5">
                      <span className={`w-2.5 h-2.5 rounded-sm border ${item.isVeg !== false ? 'border-emerald-500 bg-emerald-500' : 'border-rose-500 bg-rose-500'}`}></span>
                      {item.title}
                    </h3>
                    <p className="text-xs text-stone-400 line-clamp-2 mt-1 leading-relaxed">
                      {item.description}
                    </p>
                  </div>
                  <span className="text-base font-bold font-mono text-amber-400 ml-3 whitespace-nowrap">
                    ₹{item.price}
                  </span>
                </div>

                {/* Edit & Delete Actions */}
                <div className="flex gap-2 mt-auto pt-3 border-t border-stone-800">
                  <button
                    onClick={() => handleOpenEdit(item)}
                    className="flex-1 flex justify-center items-center gap-1.5 py-1.5 rounded-xl bg-stone-800 hover:bg-stone-700 text-stone-200 text-xs font-semibold transition-colors cursor-pointer border border-stone-700/60"
                  >
                    <Edit3 className="w-3.5 h-3.5" />
                    <span>Edit</span>
                  </button>
                  <button
                    onClick={() => handleDelete(item.id)}
                    className="flex-1 flex justify-center items-center gap-1.5 py-1.5 rounded-xl bg-stone-950 hover:bg-rose-950/40 text-stone-400 hover:text-rose-400 text-xs font-semibold transition-colors border border-stone-800 hover:border-rose-800/50 cursor-pointer"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                    <span>Delete</span>
                  </button>
                </div>
              </div>
            );
          })}
        </div>

        {/* Add/Edit Item Form Panel */}
        <div
          className={`w-full xl:w-96 bg-stone-900/90 backdrop-blur-md rounded-2xl border border-stone-800 p-6 sticky top-24 h-fit shadow-2xl ${
            isMobileFormOpen ? 'block' : 'hidden xl:block'
          }`}
        >
          <div className="flex justify-between items-center mb-4 pb-3 border-b border-stone-800">
            <h3 className="text-base font-bold font-serif text-stone-100 flex items-center gap-2">
              <Utensils className="w-4 h-4 text-amber-400" />
              <span>{editingId ? 'Edit Dish' : 'Create New Dish'}</span>
            </h3>
            {isMobileFormOpen && (
              <button
                onClick={handleResetForm}
                className="xl:hidden text-stone-400 hover:text-stone-100"
              >
                <X className="w-5 h-5" />
              </button>
            )}
          </div>

          <form onSubmit={handleSaveItem} className="flex flex-col gap-4 text-xs">
            {/* Image URL / Preview */}
            <div className="flex flex-col gap-1">
              <label className="text-stone-400 font-medium flex items-center gap-1">
                <ImageIcon className="w-3.5 h-3.5 text-amber-400" />
                Image URL
              </label>
              <input
                type="text"
                placeholder="https://images.unsplash.com/..."
                value={imageUrl}
                onChange={(e) => setImageUrl(e.target.value)}
                className="w-full bg-stone-950 border border-stone-800 focus:border-amber-500 focus:outline-none px-3 py-2 rounded-xl text-stone-200 transition-colors"
              />
              {imageUrl && (
                <div className="h-28 w-full rounded-xl overflow-hidden mt-2 border border-stone-800">
                  <img src={imageUrl} alt="Preview" className="w-full h-full object-cover" />
                </div>
              )}
            </div>

            {/* Title & Price */}
            <div className="flex flex-col gap-1">
              <label className="text-stone-400 font-medium">Dish Title</label>
              <input
                type="text"
                required
                placeholder="e.g. Royal Shahi Paneer"
                value={itemName}
                onChange={(e) => setItemName(e.target.value)}
                className="w-full bg-stone-950 border border-stone-800 focus:border-amber-500 focus:outline-none px-3 py-2 rounded-xl text-stone-200 transition-colors"
              />
            </div>

            <div className="flex gap-3">
              <div className="flex flex-col gap-1 flex-1">
                <label className="text-stone-400 font-medium">Price (₹ INR)</label>
                <input
                  type="number"
                  required
                  placeholder="320"
                  value={price}
                  onChange={(e) => setPrice(e.target.value)}
                  className="w-full bg-stone-950 border border-stone-800 focus:border-amber-500 focus:outline-none px-3 py-2 rounded-xl text-stone-200 transition-colors font-mono"
                />
              </div>

              <div className="flex flex-col gap-1 flex-1">
                <label className="text-stone-400 font-medium">Category</label>
                <select
                  value={category}
                  onChange={(e) => setCategory(e.target.value as any)}
                  className="w-full bg-stone-950 border border-stone-800 focus:border-amber-500 focus:outline-none px-3 py-2 rounded-xl text-stone-200 transition-colors"
                >
                  <option value="Starters">Starters</option>
                  <option value="Mains">Mains</option>
                  <option value="Breads">Breads</option>
                  <option value="Desserts">Desserts</option>
                  <option value="Beverages">Beverages</option>
                </select>
              </div>
            </div>

            {/* Veg / Non-Veg Toggle & Spice Level */}
            <div className="grid grid-cols-2 gap-3">
              <div className="flex flex-col gap-1">
                <label className="text-stone-400 font-medium">Dietary Type</label>
                <div className="flex items-center gap-2 bg-stone-950 p-1.5 rounded-xl border border-stone-800">
                  <button
                    type="button"
                    onClick={() => setIsVeg(true)}
                    className={`flex-1 py-1 rounded-lg text-[11px] font-bold cursor-pointer transition-all ${
                      isVeg ? 'bg-emerald-950/80 text-emerald-400 border border-emerald-800' : 'text-stone-500'
                    }`}
                  >
                    Veg
                  </button>
                  <button
                    type="button"
                    onClick={() => setIsVeg(false)}
                    className={`flex-1 py-1 rounded-lg text-[11px] font-bold cursor-pointer transition-all ${
                      !isVeg ? 'bg-rose-950/80 text-rose-400 border border-rose-800' : 'text-stone-500'
                    }`}
                  >
                    Non-Veg
                  </button>
                </div>
              </div>

              <div className="flex flex-col gap-1">
                <label className="text-stone-400 font-medium">Spice Level</label>
                <select
                  value={spiceLevel}
                  onChange={(e) => setSpiceLevel(e.target.value)}
                  className="w-full bg-stone-950 border border-stone-800 focus:border-amber-500 focus:outline-none px-3 py-2 rounded-xl text-stone-200 transition-colors"
                >
                  <option value="Mild">Mild</option>
                  <option value="Medium">Medium</option>
                  <option value="Spicy">Spicy</option>
                  <option value="Fiery Hot">Fiery Hot</option>
                </select>
              </div>
            </div>

            <div className="flex flex-col gap-1">
              <label className="text-stone-400 font-medium">Description</label>
              <textarea
                rows={3}
                placeholder="List ingredients, preparation style, allergen notes..."
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                className="w-full bg-stone-950 border border-stone-800 focus:border-amber-500 focus:outline-none px-3 py-2 rounded-xl text-stone-200 transition-colors resize-none"
              />
            </div>

            <div className="flex flex-col gap-2">
              <label className="text-stone-400 font-medium">Availability Status</label>
              <div className="flex gap-4 items-center bg-stone-950 p-2 rounded-xl border border-stone-800">
                <label className="flex items-center gap-2 cursor-pointer text-stone-200">
                  <input
                    type="radio"
                    name="status"
                    checked={status === 'Available'}
                    onChange={() => setStatus('Available')}
                    className="accent-amber-500"
                  />
                  <span>Available</span>
                </label>
                <label className="flex items-center gap-2 cursor-pointer text-stone-200">
                  <input
                    type="radio"
                    name="status"
                    checked={status === 'Sold Out'}
                    onChange={() => setStatus('Sold Out')}
                    className="accent-amber-500"
                  />
                  <span>Sold Out</span>
                </label>
              </div>
            </div>

            <div className="flex gap-2 mt-3">
              <button
                type="button"
                onClick={handleResetForm}
                className="flex-1 bg-stone-800 hover:bg-stone-700 border border-stone-700/60 text-stone-300 font-bold py-2.5 rounded-xl transition-colors cursor-pointer"
              >
                Cancel
              </button>
              <button
                type="submit"
                className="flex-1 bg-amber-500 hover:bg-amber-400 text-stone-950 font-bold py-2.5 rounded-xl transition-all shadow-lg shadow-amber-500/20 cursor-pointer"
              >
                {editingId ? 'Update Item' : 'Save Item'}
              </button>
            </div>
          </form>
        </div>
      </div>

      {/* Mobile FAB */}
      <button
        onClick={() => setIsMobileFormOpen(true)}
        className="xl:hidden fixed bottom-20 right-4 bg-amber-500 text-stone-950 p-4 rounded-full shadow-2xl hover:bg-amber-400 transition-transform active:scale-95 z-40 flex items-center justify-center cursor-pointer"
      >
        <Plus className="w-6 h-6 stroke-[3]" />
      </button>
    </div>
  );
};

