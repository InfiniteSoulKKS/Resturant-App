import React, { useEffect, useState, useRef } from 'react';
import { MenuItem, RecipeIngredient, Ingredient } from '../types';
import {
  Plus,
  Edit3,
  Trash2,
  X,
  Sparkles,
  Utensils,
  Image as ImageIcon,
  CheckCircle2,
  AlertCircle,
  Scale,
  Search,
} from 'lucide-react';
import {
  staffListMenu,
  staffCreateMenuItem,
  staffUpdateMenuItem,
  staffDeleteMenuItem,
  getMenuItemIngredients,
  searchIngredients,
  createIngredient,
  toggleSoldOut,
} from '../lib/apiClient';

interface MenuManagementProps {
  restaurantId: string;
  /** False for chef-only accounts — hides add/edit/delete and price editing. */
  canManage?: boolean;
}

export const MenuManagement: React.FC<MenuManagementProps> = ({ restaurantId, canManage = true }) => {
  const [menuItems, setMenuItems] = useState<MenuItem[]>([]);
  const [selectedCategory, setSelectedCategory] = useState<string>('All Items');
  const [editingId, setEditingId] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [message, setMessage] = useState<{ type: 'ok' | 'err'; text: string } | null>(null);

  const [itemName, setItemName] = useState('');
  const [price, setPrice] = useState('');
  const [category, setCategory] = useState<'Appetizers' | 'Mains' | 'Desserts' | 'Beverages' | 'Breads' | 'Starters'>('Mains');
  const [description, setDescription] = useState('');
  const [status, setStatus] = useState<'Available' | 'Sold Out'>('Available');
  const [imageUrl, setImageUrl] = useState('');
  const [isVeg, setIsVeg] = useState<boolean>(true);
  const [spiceLevel, setSpiceLevel] = useState<string>('Medium');
  const [isMobileFormOpen, setIsMobileFormOpen] = useState(false);

  // Recipe rows: ingredient per plate (used for pre-order ingredient estimation).
  const [recipe, setRecipe] = useState<RecipeIngredient[]>([]);

  // Ingredient selector state
  const [availableIngredients, setAvailableIngredients] = useState<Ingredient[]>([]);
  const [ingredientSearchQuery, setIngredientSearchQuery] = useState('');
  const [showIngredientSelector, setShowIngredientSelector] = useState<number | null>(null);
  const [showInlineCreate, setShowInlineCreate] = useState<number | null>(null);
  const [inlineForm, setInlineForm] = useState({ name: '', unit: 'g', category: '' });

  const categories = ['All Items', 'Starters', 'Mains', 'Breads', 'Desserts', 'Beverages'];

  const load = async () => {
    try {
      const items = await staffListMenu(restaurantId);
      setMenuItems(items);
    } catch (err: any) {
      setMessage({ type: 'err', text: `Failed to load menu: ${err.message}` });
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    if (restaurantId) load();
  }, [restaurantId]);

  // Load active ingredients for the selector
  useEffect(() => {
    if (!restaurantId) return;
    searchIngredients(ingredientSearchQuery, restaurantId, false)
      .then(setAvailableIngredients)
      .catch(() => setAvailableIngredients([]));
  }, [restaurantId, ingredientSearchQuery]);

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
    setImageUrl(item.imageUrl || '');
    setIsVeg(item.isVeg !== false);
    setSpiceLevel(item.spiceLevel || 'Medium');
    setIsMobileFormOpen(true);
    // Load the dish's existing recipe so edits don't wipe it out.
    getMenuItemIngredients(item.id)
      .then(setRecipe)
      .catch(() => setRecipe([]));
  };

  const handleResetForm = () => {
    setEditingId(null);
    setItemName(''); setPrice(''); setCategory('Mains'); setDescription('');
    setStatus('Available'); setImageUrl(''); setIsVeg(true); setSpiceLevel('Medium');
    setRecipe([]);
    setIsMobileFormOpen(false);
    setShowIngredientSelector(null);
    setShowInlineCreate(null);
  };

  // Handle selecting an ingredient from the dropdown
  const handleSelectIngredient = (rowIndex: number, ingredient: Ingredient) => {
    setRecipe((prev) => prev.map((x, j) => j === rowIndex ? {
      ...x,
      ingredientId: ingredient.id,
      name: ingredient.displayName || ingredient.name,
      unit: ingredient.unit,
    } : x));
    setShowIngredientSelector(null);
    setIngredientSearchQuery('');
  };

  // Handle inline ingredient creation from recipe editor
  const handleInlineCreateIngredient = async (rowIndex: number) => {
    try {
      const created = await createIngredient({
        name: inlineForm.name,
        displayName: inlineForm.name,
        unit: inlineForm.unit,
        category: inlineForm.category || undefined,
        stockQuantity: 0,
        reorderLevel: 0,
      }, restaurantId);
      // Auto-select the newly created ingredient
      setRecipe((prev) => prev.map((x, j) => j === rowIndex ? {
        ...x,
        ingredientId: created.id,
        name: created.displayName || created.name,
        unit: created.unit,
      } : x));
      setShowInlineCreate(null);
      setInlineForm({ name: '', unit: 'g', category: '' });
      // Refresh available ingredients
      const fresh = await searchIngredients('', restaurantId, false);
      setAvailableIngredients(fresh);
    } catch (err: any) {
      setMessage({ type: 'err', text: `❌ ${err.message}` });
    }
  };

  const handleSaveItem = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!itemName.trim() || !price) return;

    const numPrice = parseFloat(price) || 0;
    const finalImageUrl = imageUrl.trim() || 'https://images.unsplash.com/photo-1546069901-ba9599a7e63c?auto=format&fit=crop&q=80&w=800';

    const payload = {
      title: itemName,
      price: numPrice,
      category,
      description,
      status,
      imageUrl: finalImageUrl,
      isVeg,
      spiceLevel,
      restaurantId,
      // Recipe per plate — the kitchen uses this for pre-order ingredient
      // estimation. Only valid rows (name + qty + unit) are sent.
      ingredients: recipe
        .filter((r) => r.name.trim() && r.quantityPerUnit > 0 && r.unit.trim())
        .map((r) => ({ name: r.name.trim(), quantityPerUnit: r.quantityPerUnit, unit: r.unit.trim() })),
    };

    try {
      if (editingId) {
        await staffUpdateMenuItem(editingId, payload);
        setMessage({ type: 'ok', text: '✅ Menu item updated' });
      } else {
        await staffCreateMenuItem(payload);
        setMessage({ type: 'ok', text: '✅ Menu item created' });
      }
      handleResetForm();
      await load();
    } catch (err: any) {
      setMessage({ type: 'err', text: `❌ ${err.message}` });
    }
  };

  const handleDelete = async (id: string) => {
    if (confirm('Delete this dish from the menu?')) {
      try {
        await staffDeleteMenuItem(id, restaurantId);
        await load();
      } catch (err: any) {
        setMessage({ type: 'err', text: `❌ ${err.message}` });
      }
    }
  };

  if (!restaurantId) {
    return (
      <div className="pt-20 text-center py-20 text-stone-500 text-sm">
        Select a restaurant to manage its menu.
      </div>
    );
  }

  return (
    <div className="pt-20 px-4 md:px-8 mb-24 md:mb-12 max-w-[1440px] mx-auto">
      <div className="flex justify-between items-end pb-4 border-b border-stone-800">
        <div>
          <h2 className="text-2xl md:text-3xl font-bold font-serif text-stone-100 tracking-tight flex items-center gap-2">
            <Sparkles className="w-7 h-7 text-amber-400" />
            <span>Menu Management</span>
          </h2>
          <p className="text-xs text-stone-400 mt-1">
            Manage dishes, stock status, and pricing for your restaurant.
          </p>
        </div>
        {canManage ? (
          <button
            onClick={() => { handleResetForm(); setIsMobileFormOpen(true); }}
            className="hidden md:flex items-center gap-2 bg-amber-500 hover:bg-amber-400 text-stone-950 text-xs font-bold px-4 py-2.5 rounded-xl transition-all shadow-lg shadow-amber-500/20 cursor-pointer"
          >
            <Plus className="w-4 h-4 stroke-[3]" />
            Add Dish
          </button>
        ) : (
          <span className="hidden md:block text-[10px] font-mono text-stone-500 border border-stone-800 rounded-xl px-3 py-2">
            👁 Read-only — menu changes require a Manager or Admin
          </span>
        )}
      </div>

      {message && (
        <div className={`mt-4 mb-4 p-3 rounded-xl border text-xs flex items-center gap-2 ${
          message.type === 'ok'
            ? 'bg-emerald-500/10 border-emerald-500/30 text-emerald-400'
            : 'bg-rose-500/10 border-rose-500/30 text-rose-400'
        }`}>
          {message.type === 'ok' ? <CheckCircle2 className="w-4 h-4 shrink-0" /> : <AlertCircle className="w-4 h-4 shrink-0" />}
          {message.text}
        </div>
      )}

      <div className="flex gap-2 overflow-x-auto pb-2 hide-scrollbar my-4">
        {categories.map((cat) => (
          <button
            key={cat}
            onClick={() => setSelectedCategory(cat)}
            className={`px-4 py-2 rounded-xl text-xs font-medium whitespace-nowrap transition-all cursor-pointer ${
              selectedCategory === cat
                ? 'bg-amber-500 text-stone-950 font-bold shadow-md shadow-amber-500/20'
                : 'bg-stone-900 text-stone-400 hover:text-stone-100 hover:bg-stone-800/60 border border-stone-800'
            }`}
          >
            {cat}
          </button>
        ))}
      </div>

      <div className="flex flex-col xl:flex-row gap-6 h-full pb-12">
        <div className="flex-1 grid grid-cols-1 md:grid-cols-2 gap-4 content-start">
          {filteredItems.map((item) => {
            const isOut = item.status === 'Sold Out';
            return (
              <div key={item.id} className={`bg-stone-900/80 backdrop-blur-md rounded-2xl p-4 flex flex-col gap-3 border border-stone-800 shadow-xl hover:border-stone-700/80 transition-all ${isOut ? 'opacity-70' : ''}`}>
                <div className={`h-36 w-full rounded-xl overflow-hidden bg-stone-950 relative ${isOut ? 'grayscale' : ''}`}>
                  <img src={item.imageUrl} alt={item.title} className="w-full h-full object-cover" referrerPolicy="no-referrer" />
                  {isOut && <div className="absolute top-2 left-2 bg-rose-950/90 text-rose-400 border border-rose-800/80 px-2 py-0.5 rounded-lg text-[10px] font-bold uppercase">Sold Out</div>}
                  <div className="absolute top-2 right-2 bg-stone-950/80 backdrop-blur-md px-2 py-0.5 rounded-lg text-[10px] text-amber-400 font-bold uppercase border border-stone-700/50">{item.category}</div>
                </div>
                <div className="flex justify-between items-start pt-1">
                  <div className="flex-1">
                    <h3 className="text-sm font-bold font-serif text-stone-100 flex items-center gap-1.5">
                      <span className={`w-2.5 h-2.5 rounded-sm border ${item.isVeg !== false ? 'border-emerald-500 bg-emerald-500' : 'border-rose-500 bg-rose-500'}`} />
                      {item.title}
                    </h3>
                    <p className="text-xs text-stone-400 line-clamp-2 mt-1">{item.description}</p>
                  </div>
                  <span className="text-base font-bold font-mono text-amber-400 ml-3 whitespace-nowrap">₹{item.price}</span>
                </div>
                {canManage ? (
                  <div className="flex gap-2 mt-auto pt-3 border-t border-stone-800">
                    <button onClick={() => handleOpenEdit(item)}
                      className="flex-1 flex justify-center items-center gap-1.5 py-1.5 rounded-xl bg-stone-800 hover:bg-stone-700 text-stone-200 text-xs font-semibold transition-colors cursor-pointer border border-stone-700/60">
                      <Edit3 className="w-3.5 h-3.5" /> Edit
                    </button>
                    <button onClick={async () => {
                      try {
                        await toggleSoldOut(item.id, item.status !== 'Sold Out');
                        setMenuItems(prev => prev.map(m => m.id === item.id ? { ...m, status: m.status === 'Sold Out' ? 'Available' : 'Sold Out' } : m));
                      } catch (err) { console.error(err); }
                    }}
                      className={`flex-1 flex justify-center items-center gap-1.5 py-1.5 rounded-xl text-xs font-semibold transition-colors border cursor-pointer ${
                        isOut ? 'bg-emerald-950/40 hover:bg-emerald-900/40 text-emerald-400 border-emerald-800/50' : 'bg-stone-950 hover:bg-amber-950/40 text-stone-400 hover:text-amber-400 border-stone-800 hover:border-amber-800/50'
                      }`}>
                      {isOut ? <><CheckCircle2 className="w-3.5 h-3.5" /> Restock</> : <><AlertCircle className="w-3.5 h-3.5" /> 86</>}
                    </button>
                    <button onClick={() => handleDelete(item.id)}
                      className="flex-1 flex justify-center items-center gap-1.5 py-1.5 rounded-xl bg-stone-950 hover:bg-rose-950/40 text-stone-400 hover:text-rose-400 text-xs font-semibold transition-colors border border-stone-800 hover:border-rose-800/50 cursor-pointer">
                      <Trash2 className="w-3.5 h-3.5" /> Delete
                    </button>
                  </div>
                ) : (
                  <div className="flex gap-2 mt-auto pt-3 border-t border-stone-800">
                    <span className="flex-1 text-center py-1.5 rounded-xl bg-stone-950/60 text-stone-500 text-[10px] font-mono border border-stone-800">
                      Read-only · Manager/Admin can edit
                    </span>
                  </div>
                )}
              </div>
            );
          })}
        </div>

        {/* Add/Edit form — rendered for managers/admins only. Chefs get a read-only panel instead. */}
        {!canManage ? (
          <div className="w-full xl:w-96 bg-stone-900/90 backdrop-blur-md rounded-2xl border border-stone-800 p-6 sticky top-24 h-fit shadow-2xl">
            <h3 className="text-base font-bold font-serif text-stone-100">Menu is read-only</h3>
            <p className="text-xs text-stone-400 mt-2">
              Chef accounts can view the menu but cannot change prices, availability, or dish details.
              Ask a Manager or Admin to make changes.
            </p>
          </div>
        ) : (
        <div className={`w-full xl:w-96 bg-stone-900/90 backdrop-blur-md rounded-2xl border border-stone-800 p-6 sticky top-24 h-fit shadow-2xl ${isMobileFormOpen ? 'block' : 'hidden xl:block'}`}>
          <div className="flex justify-between items-center mb-4 pb-3 border-b border-stone-800">
            <h3 className="text-base font-bold font-serif text-stone-100 flex items-center gap-2">
              <Utensils className="w-4 h-4 text-amber-400" />
              <span>{editingId ? 'Edit Dish' : 'Create Dish'}</span>
            </h3>
            {isMobileFormOpen && (
              <button onClick={handleResetForm} className="xl:hidden text-stone-400 hover:text-stone-100"><X className="w-5 h-5" /></button>
            )}
          </div>
          <form onSubmit={handleSaveItem} className="flex flex-col gap-4 text-xs">
            <div className="flex flex-col gap-1">
              <label className="text-stone-400 font-medium flex items-center gap-1"><ImageIcon className="w-3.5 h-3.5 text-amber-400" /> Image URL</label>
              <input type="text" placeholder="https://..." value={imageUrl} onChange={(e) => setImageUrl(e.target.value)}
                className="w-full bg-stone-950 border border-stone-800 focus:border-amber-500 focus:outline-none px-3 py-2 rounded-xl text-stone-200" />
              {imageUrl && <div className="h-28 w-full rounded-xl overflow-hidden mt-2 border border-stone-800"><img src={imageUrl} alt="Preview" className="w-full h-full object-cover" /></div>}
            </div>
            <div className="flex flex-col gap-1">
              <label className="text-stone-400 font-medium">Dish Title</label>
              <input type="text" required placeholder="e.g. Butter Chicken" value={itemName} onChange={(e) => setItemName(e.target.value)}
                className="w-full bg-stone-950 border border-stone-800 focus:border-amber-500 focus:outline-none px-3 py-2 rounded-xl text-stone-200" />
            </div>
            <div className="flex gap-3">
              <div className="flex flex-col gap-1 flex-1">
                <label className="text-stone-400 font-medium">Price (₹)</label>
                <input type="number" required placeholder="420" value={price} onChange={(e) => setPrice(e.target.value)}
                  className="w-full bg-stone-950 border border-stone-800 focus:border-amber-500 focus:outline-none px-3 py-2 rounded-xl text-stone-200 font-mono" />
              </div>
              <div className="flex flex-col gap-1 flex-1">
                <label className="text-stone-400 font-medium">Category</label>
                <select value={category} onChange={(e) => setCategory(e.target.value as any)}
                  className="w-full bg-stone-950 border border-stone-800 focus:border-amber-500 focus:outline-none px-3 py-2 rounded-xl text-stone-200">
                  <option value="Starters">Starters</option>
                  <option value="Mains">Mains</option>
                  <option value="Breads">Breads</option>
                  <option value="Desserts">Desserts</option>
                  <option value="Beverages">Beverages</option>
                </select>
              </div>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div className="flex flex-col gap-1">
                <label className="text-stone-400 font-medium">Dietary</label>
                <div className="flex items-center gap-2 bg-stone-950 p-1.5 rounded-xl border border-stone-800">
                  <button type="button" onClick={() => setIsVeg(true)}
                    className={`flex-1 py-1 rounded-lg text-[11px] font-bold cursor-pointer transition-all ${isVeg ? 'bg-emerald-950/80 text-emerald-400 border border-emerald-800' : 'text-stone-500'}`}>Veg</button>
                  <button type="button" onClick={() => setIsVeg(false)}
                    className={`flex-1 py-1 rounded-lg text-[11px] font-bold cursor-pointer transition-all ${!isVeg ? 'bg-rose-950/80 text-rose-400 border border-rose-800' : 'text-stone-500'}`}>Non-Veg</button>
                </div>
              </div>
              <div className="flex flex-col gap-1">
                <label className="text-stone-400 font-medium">Spice</label>
                <select value={spiceLevel} onChange={(e) => setSpiceLevel(e.target.value)}
                  className="w-full bg-stone-950 border border-stone-800 focus:border-amber-500 focus:outline-none px-3 py-2 rounded-xl text-stone-200">
                  <option value="Mild">Mild</option>
                  <option value="Medium">Medium</option>
                  <option value="Spicy">Spicy</option>
                  <option value="Fiery Hot">Fiery Hot</option>
                </select>
              </div>
            </div>
            <div className="flex flex-col gap-1">
              <label className="text-stone-400 font-medium">Description</label>
              <textarea rows={3} placeholder="Ingredients, notes..." value={description} onChange={(e) => setDescription(e.target.value)}
                className="w-full bg-stone-950 border border-stone-800 focus:border-amber-500 focus:outline-none px-3 py-2 rounded-xl text-stone-200 resize-none" />
            </div>

            {/* Recipe per plate — drives pre-order ingredient estimation */}
            <div className="flex flex-col gap-2">
              <div className="flex items-center justify-between">
                <label className="text-stone-400 font-medium flex items-center gap-1">
                  <Scale className="w-3.5 h-3.5 text-amber-400" /> Recipe (per plate)
                </label>
                <button
                  type="button"
                  onClick={() => setRecipe((prev) => [...prev, { name: '', quantityPerUnit: 0, unit: 'g' }])}
                  className="text-[11px] font-semibold text-amber-400 hover:text-amber-300 flex items-center gap-1 cursor-pointer"
                >
                  <Plus className="w-3 h-3 stroke-[3]" /> Add ingredient
                </button>
              </div>
              <p className="text-[10px] text-stone-500 -mt-1">
                e.g. Rice 500 g, Chicken 250 g — used to auto-estimate ingredients from pre-orders.
              </p>
              {recipe.map((r, i) => (
                <div key={i} className="flex items-center gap-1.5 relative">
                  {/* Ingredient selector */}
                  <div className="flex-1 min-w-0 relative">
                    {showIngredientSelector === i ? (
                      <div className="absolute z-30 top-0 left-0 w-full bg-stone-900 border border-stone-700 rounded-xl shadow-xl max-h-48 overflow-y-auto">
                        <div className="sticky top-0 bg-stone-900 p-2 border-b border-stone-800">
                          <input
                            type="text"
                            placeholder="Search ingredients..."
                            value={ingredientSearchQuery}
                            onChange={(e) => setIngredientSearchQuery(e.target.value)}
                            className="w-full px-2 py-1.5 bg-stone-950 border border-stone-800 rounded-lg text-[11px] text-stone-200 focus:outline-none focus:border-amber-500"
                            autoFocus
                          />
                        </div>
                        {availableIngredients.length === 0 ? (
                          <div className="p-2">
                            <p className="text-[10px] text-stone-500 text-center">No ingredients found.</p>
                            <button
                              type="button"
                              onClick={() => { setShowIngredientSelector(null); setShowInlineCreate(i); setInlineForm({ name: ingredientSearchQuery, unit: 'g', category: '' }); }}
                              className="w-full mt-1 text-[10px] text-amber-400 hover:text-amber-300 font-bold py-1.5 rounded-lg bg-amber-500/10 border border-amber-500/30 cursor-pointer"
                            >
                              + Create &quot;{ingredientSearchQuery || 'new ingredient'}&quot;
                            </button>
                          </div>
                        ) : (
                          <>
                            {availableIngredients.map((ing) => (
                              <button
                                key={ing.id}
                                type="button"
                                onClick={() => handleSelectIngredient(i, ing)}
                                className="w-full text-left px-3 py-2 text-[11px] text-stone-200 hover:bg-stone-800 transition-colors flex items-center justify-between cursor-pointer"
                              >
                                <span>{ing.displayName || ing.name}</span>
                                <span className="text-[9px] text-stone-500 font-mono">{ing.unit}</span>
                              </button>
                            ))}
                            <button
                              type="button"
                              onClick={() => { setShowIngredientSelector(null); setShowInlineCreate(i); setInlineForm({ name: ingredientSearchQuery, unit: 'g', category: '' }); }}
                              className="w-full text-left px-3 py-2 text-[10px] text-amber-400 hover:text-amber-300 font-bold border-t border-stone-800 cursor-pointer"
                            >
                              + Create New Ingredient
                            </button>
                          </>
                        )}
                        <button
                          type="button"
                          onClick={() => { setShowIngredientSelector(null); setIngredientSearchQuery(''); }}
                          className="w-full text-[10px] text-stone-500 hover:text-stone-300 py-1.5 border-t border-stone-800 cursor-pointer"
                        >
                          Cancel
                        </button>
                      </div>
                    ) : (
                      <button
                        type="button"
                        onClick={() => { setShowIngredientSelector(i); setIngredientSearchQuery(''); }}
                        className="w-full text-left bg-stone-950 border border-stone-800 focus:border-amber-500 px-2.5 py-2 rounded-xl text-stone-200 hover:border-stone-700 transition-colors cursor-pointer"
                      >
                        {r.name ? (
                          <span className="flex items-center gap-2">
                            <span className="text-[11px]">{r.name}</span>
                            {r.ingredientId && <span className="text-[8px] text-stone-500 font-mono">✓ linked</span>}
                          </span>
                        ) : (
                          <span className="text-[11px] text-stone-500">Select ingredient...</span>
                        )}
                      </button>
                    )}
                  </div>
                  <input
                    type="number"
                    min="0"
                    step="0.001"
                    placeholder="Qty"
                    value={r.quantityPerUnit || ''}
                    onChange={(e) => setRecipe((prev) => prev.map((x, j) => j === i ? { ...x, quantityPerUnit: parseFloat(e.target.value) || 0 } : x))}
                    className="w-16 bg-stone-950 border border-stone-800 focus:border-amber-500 focus:outline-none px-2 py-2 rounded-xl text-stone-200 font-mono"
                  />
                  <select
                    value={r.unit}
                    onChange={(e) => setRecipe((prev) => prev.map((x, j) => j === i ? { ...x, unit: e.target.value } : x))}
                    className="w-16 bg-stone-950 border border-stone-800 focus:border-amber-500 focus:outline-none px-1 py-2 rounded-xl text-stone-200"
                  >
                    <option value="g">g</option>
                    <option value="kg">kg</option>
                    <option value="ml">ml</option>
                    <option value="L">L</option>
                    <option value="count">count</option>
                  </select>
                  <button
                    type="button"
                    onClick={() => setRecipe((prev) => prev.filter((_, j) => j !== i))}
                    className="text-stone-500 hover:text-rose-400 p-1 cursor-pointer"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                  </button>
                </div>
              ))}
              {recipe.length === 0 && (
                <p className="text-[10px] text-stone-600">No recipe yet — pre-order estimates will be empty for this dish until you add ingredients.</p>
              )}
            </div>

            {/* Inline Ingredient Creation Modal */}
            {showInlineCreate !== null && (
              <div className="bg-stone-950 p-3 rounded-xl border border-amber-500/30 space-y-2">
                <p className="text-[10px] text-amber-400 font-bold">Create New Ingredient</p>
                <input
                  type="text"
                  placeholder="Ingredient name"
                  value={inlineForm.name}
                  onChange={(e) => setInlineForm({ ...inlineForm, name: e.target.value })}
                  className="w-full px-3 py-2 bg-stone-900 border border-stone-800 rounded-xl text-[11px] text-stone-200 focus:outline-none focus:border-amber-500"
                  autoFocus
                />
                <div className="flex gap-2">
                  <select
                    value={inlineForm.unit}
                    onChange={(e) => setInlineForm({ ...inlineForm, unit: e.target.value })}
                    className="flex-1 px-3 py-2 bg-stone-900 border border-stone-800 rounded-xl text-[11px] text-stone-200"
                  >
                    <option value="g">g</option>
                    <option value="kg">kg</option>
                    <option value="ml">ml</option>
                    <option value="litre">litre</option>
                    <option value="piece">piece</option>
                  </select>
                  <select
                    value={inlineForm.category}
                    onChange={(e) => setInlineForm({ ...inlineForm, category: e.target.value })}
                    className="flex-1 px-3 py-2 bg-stone-900 border border-stone-800 rounded-xl text-[11px] text-stone-200"
                  >
                    <option value="">Category</option>
                    <option value="Grains">Grains</option>
                    <option value="Meat">Meat</option>
                    <option value="Vegetables">Vegetables</option>
                    <option value="Dairy">Dairy</option>
                    <option value="Spices">Spices</option>
                    <option value="Oils">Oils</option>
                    <option value="Other">Other</option>
                  </select>
                </div>
                <div className="flex gap-2">
                  <button
                    type="button"
                    onClick={() => { setShowInlineCreate(null); setInlineForm({ name: '', unit: 'g', category: '' }); }}
                    className="flex-1 py-2 bg-stone-800 hover:bg-stone-700 text-stone-300 text-[11px] font-bold rounded-xl cursor-pointer"
                  >
                    Cancel
                  </button>
                  <button
                    type="button"
                    onClick={() => handleInlineCreateIngredient(showInlineCreate)}
                    disabled={!inlineForm.name.trim()}
                    className="flex-1 py-2 bg-amber-500 hover:bg-amber-400 text-stone-950 text-[11px] font-bold rounded-xl cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
                  >
                    Create & Select
                  </button>
                </div>
              </div>
            )}

            <div className="flex flex-col gap-2">
              <label className="text-stone-400 font-medium">Status</label>
              <div className="flex gap-4 items-center bg-stone-950 p-2 rounded-xl border border-stone-800">
                <label className="flex items-center gap-2 cursor-pointer text-stone-200">
                  <input type="radio" name="status" checked={status === 'Available'} onChange={() => setStatus('Available')} className="accent-amber-500" /> Available
                </label>
                <label className="flex items-center gap-2 cursor-pointer text-stone-200">
                  <input type="radio" name="status" checked={status === 'Sold Out'} onChange={() => setStatus('Sold Out')} className="accent-amber-500" /> Sold Out
                </label>
              </div>
            </div>              <div className="flex gap-2 mt-3">
              <button type="button" onClick={handleResetForm}
                className="flex-1 bg-stone-800 hover:bg-stone-700 border border-stone-700/60 text-stone-300 font-bold py-2.5 rounded-xl transition-colors cursor-pointer">Cancel</button>
              <button type="submit"
                className="flex-1 bg-amber-500 hover:bg-amber-400 text-stone-950 font-bold py-2.5 rounded-xl transition-all shadow-lg shadow-amber-500/20 cursor-pointer">
                {editingId ? 'Update' : 'Save'}
              </button>
            </div>
          </form>
        </div>
        )}
      </div>

      {canManage && (
        <button onClick={() => setIsMobileFormOpen(true)}
          className="xl:hidden fixed bottom-20 right-4 bg-amber-500 text-stone-950 p-4 rounded-full shadow-2xl hover:bg-amber-400 transition-transform active:scale-95 z-40 flex items-center justify-center cursor-pointer">
          <Plus className="w-6 h-6 stroke-[3]" />
        </button>
      )}
    </div>
  );
};