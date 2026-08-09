import React, { useState } from 'react';
import { PrepItem, EstimatedRawMaterial } from '../types';
import { updatePrepCountDB } from '../lib/firebase';
import { INITIAL_RAW_MATERIALS } from '../data/initialData';
import {
  ChefHat,
  ShoppingBasket,
  CheckCircle2,
  Plus,
  Minus,
  Clock,
  Sparkles,
  Flame,
  Check,
} from 'lucide-react';

interface ChefPrepSummaryProps {
  prepItems: PrepItem[];
}

export const ChefPrepSummary: React.FC<ChefPrepSummaryProps> = ({ prepItems }) => {
  const [selectedCategory, setSelectedCategory] = useState<string>('All Categories');
  const [rawMaterials] = useState<EstimatedRawMaterial[]>(INITIAL_RAW_MATERIALS);

  const categories = ['All Categories', 'Starters', 'Mains', 'Desserts'];

  const filteredPrepItems = prepItems.filter((item) => {
    if (selectedCategory === 'All Categories') return true;
    return item.category === selectedCategory;
  });

  const handleAdjustCount = async (id: string, delta: number) => {
    await updatePrepCountDB(id, delta);
  };

  const totalRequiredAll = prepItems.reduce((acc, curr) => acc + curr.requiredCount, 0);
  const totalPreppedAll = prepItems.reduce((acc, curr) => acc + curr.preppedCount, 0);

  return (
    <div className="pt-20 w-full max-w-[1440px] px-4 md:px-8 flex flex-col gap-6 mx-auto pb-28">
      {/* Header & Key Stats */}
      <div className="flex flex-col md:flex-row justify-between items-start md:items-end gap-4">
        <div>
          <h2 className="text-2xl md:text-3xl font-bold font-serif text-stone-100 tracking-tight flex items-center gap-2">
            <ChefHat className="w-8 h-8 text-amber-400" />
            <span>Chef's Prep Summary</span>
          </h2>
          <p className="text-xs text-stone-400 mt-1">
            Consolidated kitchen prep tracking and raw material requirements for scheduled pre-orders.
          </p>
        </div>

        <div className="flex gap-4 w-full md:w-auto">
          <div className="flex-1 md:flex-none bg-stone-900/90 rounded-2xl p-4 border border-stone-800 flex flex-col justify-center min-w-[140px] shadow-xl">
            <span className="text-[10px] text-stone-400 font-bold uppercase tracking-wider">
              TOTAL REQUIRED
            </span>
            <span className="text-2xl font-mono text-amber-400 mt-1 font-bold">
              {totalRequiredAll || 142}
            </span>
          </div>

          <div className="flex-1 md:flex-none bg-stone-900/90 rounded-2xl p-4 border border-stone-800 flex flex-col justify-center min-w-[140px] shadow-xl">
            <span className="text-[10px] text-emerald-400 font-bold uppercase tracking-wider">
              PREPPED SO FAR
            </span>
            <span className="text-2xl font-mono text-emerald-400 mt-1 font-bold">
              {totalPreppedAll}
            </span>
          </div>

          <div className="flex-1 md:flex-none bg-amber-500/10 rounded-2xl p-4 border border-amber-500/20 flex flex-col justify-center min-w-[140px] shadow-xl">
            <span className="text-[10px] text-amber-300 font-bold uppercase tracking-wider">
              NEXT PEAK
            </span>
            <span className="text-2xl font-mono text-amber-400 mt-1 font-bold">
              19:30
            </span>
          </div>
        </div>
      </div>

      {/* Estimated Raw Materials Section */}
      <div className="bg-stone-900/80 backdrop-blur-md rounded-2xl p-5 border border-stone-800 shadow-xl">
        <div className="flex items-center gap-2 mb-4 pb-2 border-b border-stone-800">
          <ShoppingBasket className="w-5 h-5 text-amber-400" />
          <h3 className="text-xs font-bold text-stone-200 uppercase tracking-widest font-mono">
            Estimated Raw Materials & Daily Inventory
          </h3>
        </div>

        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          {rawMaterials.map((rm, idx) => (
            <div key={idx} className="flex flex-col bg-stone-950 p-3.5 rounded-xl border border-stone-800/80">
              <span className="text-[10px] text-stone-400 font-semibold uppercase tracking-wider">
                {rm.name}
              </span>
              <span className="text-base font-mono font-bold text-amber-400 mt-1">
                {rm.amount}
              </span>
            </div>
          ))}
        </div>
      </div>

      {/* Filters / Categories */}
      <div className="flex gap-2 overflow-x-auto hide-scrollbar pb-1">
        {categories.map((cat) => {
          const isActive = selectedCategory === cat;
          return (
            <button
              key={cat}
              onClick={() => setSelectedCategory(cat)}
              className={`text-xs px-4 py-2 rounded-xl whitespace-nowrap transition-all cursor-pointer ${
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

      {/* Grid Layout of Prep Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        {filteredPrepItems.map((item) => {
          const percent = Math.round((item.preppedCount / item.requiredCount) * 100);
          const isComplete = item.preppedCount >= item.requiredCount;
          const isPriority = item.priority === 'Priority';

          return (
            <div
              key={item.id}
              className={`bg-stone-900/80 backdrop-blur-md rounded-2xl p-5 flex flex-col gap-3 shadow-xl border transition-all ${
                isPriority
                  ? 'border-amber-500/60 ring-1 ring-amber-500/30'
                  : 'border-stone-800'
              } ${isComplete ? 'opacity-70' : ''}`}
            >
              <div className="flex justify-between items-start">
                <div className="flex gap-3">
                  <div
                    className="w-12 h-12 rounded-xl bg-stone-950 flex items-center justify-center bg-cover bg-center overflow-hidden border border-stone-800"
                    style={{ backgroundImage: `url('${item.imageUrl}')` }}
                  ></div>
                  <div>
                    <h4
                      className={`text-sm font-bold font-serif text-stone-100 ${
                        isComplete ? 'line-through text-stone-500' : ''
                      }`}
                    >
                      {item.itemName}
                    </h4>
                    <span
                      className={`text-[10px] uppercase font-bold mt-1 px-2 py-0.5 rounded-md inline-block ${
                        isPriority
                          ? 'bg-rose-500/10 text-rose-400 border border-rose-500/20'
                          : 'bg-stone-800 text-stone-400'
                      }`}
                    >
                      {item.tag}
                    </span>
                  </div>
                </div>

                <div className="text-right">
                  <span className="text-2xl font-mono text-amber-400 block leading-none font-bold">
                    {item.requiredCount}
                  </span>
                  <span className="text-[9px] font-bold text-stone-500 uppercase tracking-widest">
                    REQUIRED
                  </span>
                </div>
              </div>

              {/* Progress Bar */}
              <div className="mt-2">
                <div className="flex justify-between text-xs text-stone-400 mb-1">
                  <span>Prep Progress</span>
                  <span className="font-mono font-bold text-stone-200">
                    {item.preppedCount} / {item.requiredCount}
                  </span>
                </div>
                <div className="w-full bg-stone-950 rounded-full h-2 overflow-hidden border border-stone-800">
                  <div
                    className={`h-2 rounded-full transition-all duration-300 ${
                      isComplete
                        ? 'bg-emerald-400 shadow-sm shadow-emerald-400/50'
                        : 'bg-amber-500 shadow-sm shadow-amber-500/50'
                    }`}
                    style={{ width: `${Math.min(100, percent)}%` }}
                  ></div>
                </div>
              </div>

              {/* Controls */}
              <div className="flex justify-end gap-2 mt-2">
                {isComplete ? (
                  <span className="text-xs text-emerald-400 uppercase px-3 py-1 flex items-center gap-1 font-bold">
                    <Check className="w-4 h-4 text-emerald-400" />
                    PREPPED
                  </span>
                ) : (
                  <>
                    <button
                      onClick={() => handleAdjustCount(item.id, -1)}
                      className="text-stone-300 bg-stone-800 hover:bg-stone-700 text-xs px-3 py-1.5 rounded-xl transition-colors border border-stone-700 font-bold cursor-pointer"
                    >
                      -1
                    </button>
                    <button
                      onClick={() => handleAdjustCount(item.id, 1)}
                      className="bg-amber-500 hover:bg-amber-400 text-stone-950 text-xs px-4 py-1.5 rounded-xl shadow-md shadow-amber-500/20 transition-all font-bold cursor-pointer flex items-center gap-1"
                    >
                      <Plus className="w-3.5 h-3.5 stroke-[3]" />
                      <span>1 PREPPED</span>
                    </button>
                  </>
                )}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};

